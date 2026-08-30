# ADR-024: Deliberate Manual Monitoring Is Structured Configuration, Not Free Text

**Status:** Accepted
**Date:** 2026-08-31

## Context

Crop 13 (Tarragon) intentionally has no soil-moisture probe. Only five probes were wired, and the sixth was a deliberate choice rather than an oversight.

The deterministic engine could not tell the difference. `CropSoilMoistureAssessmentRule` treats a missing sensor assignment as a data-quality problem and raises `CROP_SENSOR_NOT_ASSIGNED`, which is correct behaviour for a crop that *should* have a probe. For Tarragon it produced a permanently actionable care loop: loop 1 opened on 2026-08-29 and could never close, because the condition it describes will never stop being true.

Outbound notification (ADR-023) is what turned this from a latent modelling gap into a visible one. A loop that can never close now generates an action-required email and a reminder every twelve hours, forever. The notification work did not cause the defect; it made an existing one impossible to ignore.

A free-text `CropObservation` already recorded that manual monitoring was intentional. That is the right place for human evidence and it must be preserved — but a deterministic rule must never read unstructured text as configuration. Doing so would mean an assessment's behaviour depends on prose that nobody validated, that has no version, and that a later observation could contradict without anyone noticing. It would also quietly make free-text notes load-bearing, so that editing a note changes what the engine does.

There is a third option we are explicitly rejecting: deleting the crop, ending it, or removing its monitoring profile to silence the assessment. Tarragon is a real, living, actively-tended crop. Making the model lie about its existence to quieten an alert would be the worst possible fix.

## Decision

Add a structured, versioned `soil_monitoring_mode` to `crop_monitoring_profile`:

- **`SENSOR`** — a probe is expected. Absence of an assignment, absence of calibration, and stale readings are all genuine problems and are reported as such. This is the default and the existing behaviour.
- **`MANUAL`** — soil condition is deliberately judged by a human looking at the plant. No probe is expected.

Under `MANUAL`, the rule raises none of `CROP_SENSOR_NOT_ASSIGNED`, `CROP_SENSOR_CALIBRATION_REQUIRED`, `CROP_SENSOR_DATA_STALE`, or any sensor-derived high/low moisture assessment.

### The mode is a profile version, not a flag

Changing the mode creates a **new monitoring-profile version** and disables the previous one, exactly as any other profile change does. The earlier version is never overwritten. This is what lets a historical assessment continue to reference the profile that actually produced it: an assessment raised while the crop was in `SENSOR` mode remains explicable after the crop moves to `MANUAL`, rather than becoming a mystery row referencing configuration that no longer exists.

Crop 13 is seeded as version 2 with mode `MANUAL`; version 1 is retained, disabled, as history. The five sensor-equipped herbs stay `SENSOR` on version 1 and are untouched.

### `MANUAL` means unknown, not fine

This is the distinction the whole change turns on, and it is easy to get wrong in a way that is actively dangerous.

Suppressing an assessment must not become an implicit claim that the soil is in good condition. A `MANUAL` crop's soil state is **unknown to the platform** — strictly less known than a sensor crop's, not more. The briefing therefore reports `soilMonitoringMode: MANUAL` with an explicit statement that soil condition requires human observation, and never presents a moisture figure, a status of `OK`, or an absence that could read as "nothing to report".

What changes is only this: a deliberate absence of a probe is no longer a **data-quality gap**. A gap means "we tried to measure and could not". Tarragon is not a failed measurement; it was never going to be measured. Listing it as a gap trains the reader to ignore the gap list, which is the section that most needs to stay trustworthy.

### Human evidence stays human evidence

The existing `CropObservation` recording the manual-monitoring intent is preserved unchanged. It remains what it always was: a human's account of what they decided and why. The new profile version is the executable form of that intent, written deliberately through a domain service by an identified actor. Evidence and configuration are now two records with two different jobs, rather than one record being asked to do both.

### Reversible through a normal, idempotent write path

A new MCP tool, `set_crop_soil_monitoring_mode`, creates the new profile version. It requires an `idempotencyKey` like every other care-domain write, takes a `rationale` so the reason is recorded rather than inferred, and preserves the superseded profile. Moving a crop back to `SENSOR` is the same operation in reverse and restores normal sensor-assignment assessment immediately — which is exactly what should happen the day a sixth probe is wired.

## Consequences

**Good:**
- Assessment 7 resolves on the next evaluation cycle, and loop 1 closes through the ordinary recovery mechanism (ADR-023's corrected, state-driven path) rather than needing a manual database edit. No reminder is ever sent for it, because notification policy only considers open loops.
- The engine can now distinguish "should have a sensor and doesn't" from "deliberately has no sensor" — a distinction it genuinely needs and could not previously express.
- The fix generalises. Any future crop tended by eye rather than by probe is one tool call, not a code change.
- Free text stays advisory. No deterministic rule reads prose.

**Costs and limits:**
- A crop in `MANUAL` mode gets **no soil safety net at all**. If someone sets the mode to silence an alert on a crop that really does have a failing probe, the platform will stop telling them about it. The `rationale` field and the preserved version history are the mitigation — the reason is recorded and the change is attributable — but this is a real way to shoot yourself in the foot, and it is deliberate: the alternative is a mode that does not actually do what it says.
- `MANUAL` suppresses *sensor-derived* soil assessment only. Temperature assessment, care loops, briefing inclusion, and every other rule are unaffected, and a `MANUAL` crop still appears in full in the daily briefing.
- If a probe is later assigned to a crop still in `MANUAL` mode, its readings are stored and reported as facts but produce no assessment. That is the honest reading of the configuration, but it may surprise someone who wires a probe and expects alerts without changing the mode.
