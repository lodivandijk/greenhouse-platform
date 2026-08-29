# ADR-021: Immutable Human-in-the-Loop Care Model

**Status:** Accepted
**Date:** 2026-08-29

## Context

Every increment so far — telemetry, twin, assessment engine, crop domain, soil moisture — has been observational: the platform senses, stores, interprets, and displays, but nothing connects "a condition was detected" to "a human did something about it and we know whether it worked." `ARCHITECTURE_PARADIGM.md` §10–16 describes exactly this direction (`Observation → Twin → Assessment → Decision → Action → Outcome → next cycle`) but marks it explicitly as **exploratory** — an idea discussed, not yet decided.

The "Daily Crop Status and Human Feedback Loop v1" spec is the first real implementation of that direction. This ADR promotes §10–16 from exploratory to decided, scoped narrowly: a human-operated loop with Claude as the conversational interface via MCP, no actuator control, no autonomous approval.

Two structural requirements drive this ADR specifically:

1. **Immutability of business history.** Assessment, decision, command, execution, and outcome records must never be overwritten — a correction, a changed mind, a re-evaluation must all produce a new linked record, not a mutation of the old one. This is what makes the loop auditable, resumable by a fresh Claude session with no prior context, and honest about what was actually decided/done versus what was later understood.
2. **Scope as its own append-only concept, separate from lifecycle.** Whether a record is relevant to a particular care loop is not the same question as whether that record is approved, resolved, or completed. A rejected decision remains valid historical evidence. A resolved assessment remains part of the reason an intervention happened. Only an explicit, reasoned override (e.g. "the probe was displaced, this reading is invalid") removes a record from a loop's effective scope — and even that is itself an append-only event, not a deletion.

This directly conflicts with the *existing* `AssessmentReconciler`, which does genuine in-place `UPDATE`s on the `assessment` table (confirmed by inspection — `applyUpdate()`/`applyResolve()` call setters then `save()` on the same entity). No event-sourced or append-only pattern exists anywhere else in this codebase.

## Decision

**New persistence paradigm, scoped to the care-loop domain and extended into the existing assessment table — not a rewrite of the whole application.**

**Assessments**: add `assessment_lifecycle_event` (full-snapshot rows: `RAISED`/`UPDATED`/`RESOLVED`/`REOPENED`), written by `AssessmentReconciler` in the same transaction as its existing upsert. The mutable `assessment` row is retained as the current-state projection — genuinely rebuildable from its own event history now, which is what makes keeping it mutable compliant with "rebuildable projections and caches may be updated in place" rather than a loophole. Applies uniformly to all assessment codes, including the four pre-existing zone-level rules (temperature, humidity, staleness, offline) — the spec's immutability requirement doesn't carve out legacy codes, and there's no reason to have two different assessment lifecycles running in parallel. One genuine behavior change: a resolved assessment whose condition recurs now reopens the same row (`REOPENED` event, `status` back to `ACTIVE`) instead of always minting a new id, since the spec explicitly names `REOPENED` as a lifecycle event type.

**Care loop domain** (new `com.greenhouse.careloop` package): `CareLoop` is the correlation root — one open condition and everything that responds to it. `Decision`, `Command`, `Execution`, `Outcome` are each immutable once created; a correction creates a new linked record (`supersedesDecisionId`, `correctsExecutionId`, `supersedesOutcomeId`) rather than editing the original. `LoopRecordScopeEvent` represents scope changes as append-only events per loop-record pair; current effective scope is the latest valid event, computed on read (not materialized — loop volumes here are a handful of crops, not worth the complexity of keeping a cache in sync). Automatic scope policy (a valid assessment opening a loop, a decision for that loop, a command from an approved decision, etc. are all auto-in-scope) means a human never manages scope for ordinary operation — overrides are exceptional and always carry a reason.

**Autonomy is deliberately narrow in this version**: Decisions require explicit human approval before a Command is generated. Commands target a human only — never an actuator. Claude may propose and relay, but may only record an approval, acknowledgement, or execution after explicit confirmation from the user in the current conversation (persisted as `actorType=HUMAN_VIA_AGENT`, distinguishing it from a hypothetical future `AGENT`-initiated action). Outcomes may be `INCONCLUSIVE` — the system never fabricates success when evidence is unavailable.

**Idempotency** is required on every new MCP write operation (a shared `IdempotencyService`/`idempotent_request` table, not per-tool), since a human/Claude retry of a watering confirmation or approval must never double-create a command or execution.

## Consequences

- This is the largest single persistence paradigm shift in the project's history — 15+ new tables, a genuinely new append-only pattern with no prior precedent to lean on. Deliberately *not* full event-sourcing of the whole application (per the spec's own explicit instruction): only the care-loop domain and the assessment lifecycle are append-only; everything else (crop, goal, harvest, twin, device) keeps its existing mutate-in-place model unchanged.
- `AssessmentReconciler`, `AssessmentEntity`, `AssessmentCode`, `AssessmentScopeType`, and `AssessmentFinding` are all extended (not replaced) — existing REST behavior (`/api/v1/assessments`, `/api/v1/state`) and the dashboard are unaffected by this change; the four pre-existing rules keep producing exactly the same findings they always have, just with an event trail now sitting underneath them.
- No actuator integration, automatic irrigation, or autonomous decision approval exists after this ADR — explicitly out of scope, and the model is deliberately shaped so that adding those later (a `Command` targeting a device instead of a human, an `AGENT`-initiated `Decision`) is a natural extension rather than a redesign.
- Claude's authority is bounded entirely by what MCP tools exist and what they require (explicit confirmation, idempotency keys, allow-listed action types) — there is no path for a conversational instruction alone to mutate a database row or approve its own proposal.

## Related / superseded decisions

Promotes `ARCHITECTURE_PARADIGM.md` §10–16 from exploratory to decided, scoped to a human-operated loop only (§11's objective-driven autonomy and §14's full reasoning loop remain exploratory beyond this). Extends ADR-004 (separate assessment engine) and the correlation-key reconciliation pattern established alongside it. Reuses the package-per-forward-facing-domain precedent from ADR-010 (`Goal`) and ADR-017 (`Action`) for the new `careloop` package. Does not supersede ADR-016's scoped-delete-capability reasoning — care-loop records are never deleted, only superseded or scoped out. See ADR-022 for the related, narrower decision to move soil-sensor calibration and crop assignment from YAML config into versioned database tables, made alongside this one because the care loop needs to resolve "which calibration was valid at evaluation time," which config redeployment can't express.
