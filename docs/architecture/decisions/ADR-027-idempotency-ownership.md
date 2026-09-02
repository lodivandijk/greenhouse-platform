# ADR-027: A Reservation Reports Who Owns It

**Status:** Accepted
**Date:** 2026-09-02

## Context

ADR-021 promised that a retried care-loop write replays its stored result rather than running twice. `IdempotencyService` said so in its own class comment: *"a repeated approval cannot issue a second command and a repeated execution report cannot create a second execution."*

The implementation could not deliver that. `reserve()` inserted a row, caught the duplicate-key violation, logged it at debug, and returned `void`. Every caller then proceeded to run the action regardless. Two simultaneous deliveries of the same request both executed it.

Sequential retries were fine — the completed row is found and replayed — so this only bit under concurrency, or after a crash. But the blast radius was uneven in a way that made it easy to miss: `command` has a unique index on `decision_id`, so a duplicate approval genuinely could not issue a second command. `execution` has no such constraint. The one operation the comment named as safe was the one nothing protected.

There was a second path, which needs no concurrency at all. If `complete()` is never reached — a crash, or the caught-and-logged failure when caching the result JSON — the row stays `IN_PROGRESS` with a null result. `findCompletedResult` maps that null to an empty `Optional`, so every later retry re-ran the action, indefinitely.

## Decision

`reserve()` returns a `Reservation`, and only the caller that receives `ACQUIRED` may run the action:

- **`ACQUIRED`** — this caller created the row and owns the action.
- **`ALREADY_COMPLETED`** — another caller finished it; replay the stored result.
- **`IN_PROGRESS`** — another caller is running it now. Refuse, and tell the caller to retry the identical call shortly.
- **`CONFLICT`** — same key, different request fingerprint. A caller bug, not a retry.

The insert lives in a separate `IdempotentRequestWriter` bean for two reasons, both learned the hard way in this codebase. Spring's transaction proxy does nothing for self-invocation, so `REQUIRES_NEW` on a method called via `this` would silently join the caller's transaction. And a unique-constraint violation marks the surrounding PostgreSQL transaction rollback-only, so the constraint violation must **escape** the transactional method before it is handled — catching it inside means the interceptor still fails on commit. The first attempt at this fix made exactly that mistake and every test failed with `UnexpectedRollbackException`.

`IN_PROGRESS` is reported rather than waited on. The caller is an agent that can retry in a moment; blocking a request thread on another request's progress would trade a duplicate-write bug for a thread-exhaustion one.

### Abandoned reservations are taken over, deliberately

A reservation older than five minutes with no recorded outcome is assumed to belong to a caller that died, and the next caller claims it and re-runs the action.

Without this, the fix would turn "might run twice" into "can never run again": one crash would permanently wedge that idempotency key, and for someone trying to record work they have actually done in a greenhouse, being permanently unable to record it is worse than recording it twice.

So the honest guarantee is: **exactly-once for concurrent and sequential retries, at-least-once for crashed ones.** That is the same trade made for notification delivery in ADR-023, and it is stated here rather than implied.

The takeover is conditional on the row still looking abandoned when it is claimed, so two reclaimers cannot both succeed.

### One implementation of the protocol

The reservation protocol was previously written out by hand at each MCP call site. That is how the defect survived: `reserve` returned void, every site ignored it, and no single place looked obviously wrong. It is now interpreted in one place, `McpIdempotency.guard`, which returns a replay response or hands ownership to the caller. Adding a third write tool cannot reintroduce the bug by copying a neighbour.

## Consequences

**Good:**
- Concurrent duplicate delivery runs the action once. Proven by a test that fires eight threads through one barrier and asserts exactly one `ACQUIRED`.
- A failure to cache a result no longer causes unbounded re-execution.
- The class comment now describes what the code does, including where the guarantee stops.

**Costs and limits:**
- A caller can now receive `IN_PROGRESS` and must retry. This is a new response an agent has to handle; the exception message says explicitly to repeat the identical call rather than reissue with a new key, since a new key would perform the action twice.
- The five-minute timeout is a guess. Too short and a genuinely slow request could be duplicated; too long and a crashed one blocks for longer than necessary. Five minutes is far beyond any real request here and short enough not to strand a person.
- `execution` still has no unique constraint. The idempotency table is now the real guard, which is a decision worth revisiting if executions ever arrive from anywhere but MCP.
