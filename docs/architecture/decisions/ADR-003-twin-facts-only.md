# ADR-003: Make the Digital Twin Facts-Only

**Status:** Accepted
**Date:** 2026-07-26

## Context

The original Digital Twin (ADR-002) computed both facts (temperature, humidity, device connectivity, freshness) and interpretation (`TOO_HOT`/`TOO_COLD`/`TOO_DRY`/`TOO_HUMID`, an aggregate `WARNING` status) in the same assembly step, inside `TwinAssembler`. This was reviewed against a larger Assessment Engine specification and identified as a problem before that engine was built: if a dedicated assessment domain was going to own interpretation (severity, thresholds, lifecycle), the twin computing its own competing interpretation would mean **two authoritative implementations of the same rule** — exactly the kind of drift the platform was trying to avoid, and precisely the "facts ≠ interpretation" boundary this project's architecture paradigm now treats as its most important current principle.

A specific open question during this review: with `EnvironmentAssessment` removed, does `TwinStatus.WARNING` still mean anything? It was previously derived from the zone-level environmental assessment being computed inside the twin — the twin literally no longer has the ability to decide "the environment is worrying" once assessment logic moves out.

## Decision

Strip all environmental interpretation out of the twin:

- Removed `EnvironmentAssessment`, `AssessmentLevel`, and `EnvironmentCondition` entirely from the twin model (`com.greenhouse.twin.model`, `com.greenhouse.twin.status`). `ZoneTwin` no longer has an `assessment` field; `GET /api/v1/twin` no longer serializes one.
- Resolved the `TwinStatus.WARNING` question by **removing `WARNING` from the enum** rather than redefining it. `TwinStatus` is now derived purely from device connectivity facts (`NORMAL`, `OFFLINE`, `UNKNOWN`) — a zone reporting a temperature above the configured limit, with its device online, now correctly reports twin status `NORMAL`. "This needs attention" became exclusively an assessment-engine concern.
- Freshness (`FreshnessStatus`) and device availability (`DeviceStatus`) remain in the twin — these were judged to be **factual derived state** (a timing calculation against a threshold, not a judgement about whether that timing is acceptable), matching the distinction the architecture paradigm draws in §7.2: *"Device is OFFLINE according to configured timing semantics"* is a twin fact; *"an offline device requires attention"* is an assessment.
- The ~10 environmental-warning-specific tests in `TwinAssemblerTest` were deleted only after equivalent coverage was confirmed to already exist in the new rule tests (`TemperatureAssessmentRuleTest`, `HumidityAssessmentRuleTest`) — matching the migration procedure the architecture paradigm and its source spec both required: add the new rule tests first, then remove the old logic, never leave two implementations running in parallel even temporarily.

## Consequences

- `GET /api/v1/twin` is now a strictly narrower, purely factual contract. Any existing client relying on the old `assessment` field or `TwinStatus.WARNING` would break — there were no external clients at this point, so no compatibility shim was needed.
- This is the change that made ADR-004 (a separate assessment engine) non-redundant — before this, adding an assessment engine alongside the twin's own interpretation would have meant two competing sources of truth for the same judgement.
- Established the precedent (now written into the architecture paradigm itself) that "is this a fact or an interpretation" is the primary question to ask when a new field is proposed for the twin.

## Related / superseded decisions

Narrows ADR-002. Enables ADR-004 (assessment engine) and ADR-005 (`/api/v1/state` recomposes what this ADR removed, for consumers that need both).
