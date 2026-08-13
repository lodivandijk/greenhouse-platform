# ADR-009: Crop Domain Added Without Reworking Telemetry

**Status:** Accepted
**Date:** 2026-08-13

## Context

The platform already has a well-established `com.greenhouse.observation` domain for machine-generated environmental telemetry (temperature/humidity/pressure, high-volume, append-only, `Instant`-timestamped). The MCP milestone needed to introduce fundamentally different information: biological/semantic evidence about a specific crop (health, flowering, harvests) that a human reports, not a sensor. Generalising the existing `Observation` model to cover both was considered and rejected — it would have coupled a high-frequency machine feed to a low-frequency, human-authored one, and forced every future telemetry query to filter out crop data (or vice versa).

## Decision

Add `Crop`, `Harvest`, and `CropObservation` as a new domain (`com.greenhouse.crop`), structurally similar to `Observation` (an envelope of `crop`/`metric`/`value`/`source`/`timestamp`) but entirely separate persistence, entities, and services. `com.greenhouse.observation` is untouched by this milestone.

## Consequences

- Existing telemetry ingestion, the Digital Twin, and the Assessment Engine all remain exactly as they were — verified live on the Pi immediately after this checkpoint deployed.
- The word "observation" now means two different things in this codebase depending on package: `com.greenhouse.observation.ObservationStatus` (machine telemetry) and `com.greenhouse.crop.CropObservation` (human/semantic evidence). This naming overlap was a deliberate, accepted trade-off — the milestone spec itself frames the distinction this way, and inventing a different word for one side seemed more likely to confuse future readers than the overlap itself.
- `CropObservation` and `Harvest` do reference `crop.id` via a real foreign key (unlike `Observation`, which deliberately has none against `device`) — crop-domain writes only ever happen through validated services, never raw device ingestion, so the resilience argument that keeps `Observation` FK-free doesn't apply here.

## Related / superseded decisions

Builds on the persistence pattern established in ADR-001, without modifying anything ADR-001 covers.
