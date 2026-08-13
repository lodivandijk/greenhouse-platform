# ADR-004: Separate Assessment Engine

**Status:** Accepted
**Date:** 2026-07-26

## Context

Once the twin's environmental interpretation was identified as something to remove (ADR-003), that logic needed somewhere authoritative to live. Beyond just relocating "too hot / too cold" checks, the platform needed something the twin fundamentally cannot be by design: a **persisted, stateful record** of "this condition has been true since X, and is it still true now?" A request-driven twin assembly has no memory between requests — it cannot answer "has this been active for 10 minutes or 10 seconds?" or guarantee a UI isn't shown a flickering, duplicated stream of the same underlying problem re-detected on every poll.

## Decision

Introduce a dedicated `com.greenhouse.assessment` domain, deliberately separate from `com.greenhouse.twin` and from `com.greenhouse.observation`:

- **Stateless rules** (`com.greenhouse.assessment.rule`): `TemperatureAssessmentRule`, `HumidityAssessmentRule`, `ObservationFreshnessAssessmentRule`, `DeviceAvailabilityAssessmentRule`. Each rule takes a `GreenhouseTwin` and a timestamp and returns a list of `AssessmentFinding`s — pure functions, no side effects, independently unit tested.
- **A stable identity per logical condition**: a correlation key (`{greenhouseId}:{scopeType}:{scopeId}:{code}`), used to recognise "this is the same problem recurring" versus "this is a new problem," rather than treating every evaluation cycle's output as a fresh, unrelated event.
- **A reconciler** (`AssessmentReconciler`) that diffs freshly computed findings against currently-persisted `ACTIVE` assessments by correlation key and raises / updates / resolves accordingly, so one logical condition is tracked as a single record across its lifecycle.
- **PostgreSQL persistence** (`assessment` table, `V3__create_assessment_table.sql`) with a **partial unique index** — `UNIQUE (correlation_key) WHERE status = 'ACTIVE'` — enforcing "at most one active record per condition" at the database level while still permitting historical recurrence (a resolved condition can become active again as a new row).
- **A scheduler** (`com.greenhouse.evaluation.GreenhouseEvaluationScheduler`, one-minute `fixedDelay`) that owns the only write path into assessment state: `GreenhouseEvaluationCoordinator` builds the current twin and calls the assessment service, which reconciles and persists. Read endpoints never trigger reconciliation as a side effect.
- **`GET /api/v1/assessments`**, sorted by severity (application-code comparator, not a JPA-derived `ORDER BY` — `AssessmentSeverity` is persisted as `EnumType.STRING`, so a raw column sort would order alphabetically rather than by actual severity rank).

Specific rule-level judgement calls made and deliberately recorded here rather than left implicit in code comments alone:
- A device that has **never** reported (`DeviceStatus.UNKNOWN`) does **not** raise `DEVICE_OFFLINE` — only a device that was previously seen and has now gone quiet (`OFFLINE`) does. "Never heard from" and "went missing" are different claims.
- `OBSERVATION_STALE` is suppressed when every device in the zone is already offline/unknown, to avoid reporting the same root cause twice under two different codes.
- Temperature/humidity rules operate at `ZONE` scope (matching how environmental limits are configured); device availability operates at `DEVICE` scope.

## Consequences

- Interpretation now has exactly one authoritative implementation, persisted and stateful, separate from the twin's stateless per-request facts.
- Introduced new operational surface: a background scheduler that writes to the database outside any HTTP request, which required deliberate test-isolation handling — the default Spring test context has the scheduler enabled (`matchIfMissing = true`), so every full-context test not explicitly opting out (`greenhouse.evaluation.enabled=false`) could otherwise pollute shared test/dev data. This is now a standing convention for any new full-context test class in this codebase.
- This ADR's design (rules, correlation key, reconciler, partial unique index) is the direct implementation of the architecture paradigm's "Assessment is interpretation" principle (§7): assessment rules consume twin facts and produce interpretation, without the twin ever being aware assessments exist.

## Related / superseded decisions

Depends on ADR-003 (twin must be facts-only first, or this would create a second competing interpretation). Consumed by ADR-005 (`/api/v1/state` composes this engine's output with the twin's facts for UI consumption).
