# ADR-005: Compose Facts and Assessments into an Application State Read Model

**Status:** Accepted
**Date:** 2026-07-26

## Context

After ADR-003 and ADR-004, the platform had two clean but separate sources of truth: `GET /api/v1/twin` (facts only) and `GET /api/v1/assessments` (active/resolved interpretations, queried independently). A UI — or any consumer needing "what's the full operational picture right now" — would otherwise have to call both endpoints and stitch them together itself, and would need to know that assessments must never be re-evaluated as a side effect of a read.

## Decision

Add a third, explicitly-composed read model in its own package, `com.greenhouse.state`, separate from both `twin` and `assessment` (the architecture paradigm's §321 rule: don't place cross-domain orchestration inside either domain it orchestrates):

- `GreenhouseStateService.getCurrentState()` calls `TwinService.getCurrentTwin()` and `AssessmentQueryService.getActiveAssessments()` and combines them into a `GreenhouseStateResponse` (`generatedAt`, `twin`, `assessments`).
- Exposed as `GET /api/v1/state`.
- Explicitly **read-only**: this endpoint queries currently-persisted active assessments, it does not run the reconciler. The evaluation scheduler (ADR-004) remains the only writer of assessment state. This was a deliberate constraint, not an oversight — a GET endpoint that could trigger side-effecting writes would violate basic REST semantics and make assessment lifecycle timing dependent on how often clients happened to poll.

This is the concrete implementation of the architecture paradigm's §8: *"a read model, not a new source of truth."*

## Consequences

- Gave downstream consumers (first the planned UI, then UI v1 itself — see ADR-006) a single endpoint that matches how a human actually wants to see greenhouse state: current facts plus what currently needs attention, together.
- Reinforced the "compose, don't collapse" pattern: when two domains' data needs to be shown together, the answer is a new composing layer above both, not merging one domain's concerns into the other's model.

## Related / superseded decisions

Depends on ADR-003 and ADR-004. Directly consumed by ADR-006 (the UI v1 dashboard's only data dependency is this endpoint).
