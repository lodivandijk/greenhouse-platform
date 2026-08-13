# ADR-002: Introduce a Digital Twin

**Status:** Accepted (later narrowed by ADR-003)
**Date:** 2026-07-25

## Context

Before this decision, "current greenhouse state" existed only implicitly: a client had to call `GET /api/v1/observations/latest` per device and `GET /api/v1/devices` separately, then reconstruct connectivity, freshness, and environmental status itself. There was no single, authoritative, request-driven answer to "what is true about the greenhouse right now?" — and no consistent place to define what "online", "stale", or "within range" actually meant. Each future consumer (a dashboard, an API client, eventually an assessment/reasoning layer) would otherwise have re-derived these rules independently and inevitably inconsistently.

## Decision

Introduce a Digital Twin: a `GreenhouseTwin` object, assembled fresh on every request by `TwinAssembler` from the latest persisted observations plus configured topology (`TwinProperties` — greenhouse id/name, zones, per-zone device ids, environmental limits, freshness/offline thresholds), exposed at `GET /api/v1/twin`.

Key design choices made at this point:
- The twin is **assembled per request**, not a persisted/cached entity — it is always a fresh derivation from the observation log plus one consistent `Instant` (via an injected `Clock` bean, not `Instant.now()`, specifically so the same logical "now" is used across the whole assembly and so tests can control time).
- Greenhouse topology (which devices belong to which zone, environmental limits) is configuration (`application.yml`), not a database table — an explicit, acknowledged trade-off given the small number of devices at this stage, expected to move to persistent configuration if the device count grows.
- At this point, the twin **combined facts and interpretation**: it computed device status, data freshness, *and* an environmental assessment (`EnvironmentAssessment` / `AssessmentLevel` / `EnvironmentCondition`, e.g. `TOO_HOT`/`TOO_COLD`/`TOO_DRY`/`TOO_HUMID`) and an aggregate `TwinStatus` that could be `WARNING`. That combination is exactly what ADR-003 later reversed.

## Consequences

- Established the twin as the single authoritative "current state" read path that the Assessment Engine (ADR-004) and the composed state endpoint (ADR-005) both build on.
- Established the `Clock`-injection and request-driven-assembly patterns that later components (the evaluation scheduler) reuse directly.
- The original combined facts+interpretation design was short-lived by design intent — it was explicitly called out during review as something to separate once an assessment domain existed, which ADR-003/ADR-004 then did the following day.

## Related / superseded decisions

Narrowed by ADR-003 (twin becomes facts-only) and complemented by ADR-004 (assessment logic moves to a dedicated domain) and ADR-005 (facts + assessments recomposed for consumers via `/api/v1/state`).
