# ADR-020: Soil Moisture Calibration Storage

**Status:** Accepted
**Date:** 2026-08-29

## Context

With all 5 soil probes wired and reporting raw ADC (ADR-018), the next step per the spec's own staging (`soil-moisture-sensor-integration-v2-spec.md` §8) is calibration: "a stable dry/air reference" and "a stable reading in the intended growing medium at a known wet condition," per probe, before any moisture percentage is ever computed.

Real data was collected for all 5 sensors on 2026-08-29 using the now-working live pipeline (readings land in `soil_moisture_reading` within 60s) rather than manual serial transcription: each probe held in air for multiple confirmed-stable observation cycles, then fully immersed in water for multiple confirmed-stable cycles, then returned to air and re-confirmed stable. Two to three consecutive cycles agreeing within a few ADC counts were treated as a valid reference point. All 5 sensors confirmed the expected direction (higher raw ADC = drier) with a consistent ~1600-unit spread between dry and wet:

| Sensor | Dry | Wet |
|---|---|---|
| `soil-01` | 2814 | 1181 |
| `soil-02` | 2706 | 1121 |
| `soil-03` | 2707 | 1105 |
| `soil-04` | 2794 | 1179 |
| `soil-05` | 2717 | 1134 |

The spec leaves the storage location open: "Store calibration per `sensorId`, preferably in Pi-side configuration or a calibration entity."

## Decision

Extend the existing `SoilSensorProperties.SoilSensorAssignment` record (ADR-018) with two nullable fields, `dryRawAdc`/`wetRawAdc`, rather than introducing a separate calibration entity/table or a second config section. This was already the natural home: that record exists precisely to hold "facts about a physical `sensorId` that live in Pi-side config, not firmware, not the database" (it already held the plant assignment), and calibration reference points are the same kind of fact — a rarely-changing, per-probe characteristic, not high-frequency telemetry.

Both fields are nullable (`Integer`, not `int`) because a sensor's assignment can exist before it's calibrated — as it did for all 5 sensors between ADR-018 (assignment added, no calibration) and now. A new probe (if `soil-06` or a replacement ever gets wired) gets an assignment entry immediately and a calibration pair only once genuinely measured, rather than being blocked on a calibration value that doesn't exist yet or forced to carry a fabricated placeholder.

**Validation**: the record's compact constructor now rejects `wetRawAdc >= dryRawAdc` when both are present — a direct codification of the empirically-confirmed direction from the paragraph above. This turns a config typo (swapping the two, or a genuinely miscalibrated probe) into a startup failure instead of a silently-wrong reference point.

**No percentage is computed anywhere.** This ADR covers storage of the two reference points only. Deriving a 0–100% value from `(dryRawAdc - rawAdc) / (dryRawAdc - wetRawAdc)` and exposing it through the twin, an assessment, or the UI remains exactly as deferred as it was before (spec §9) — nothing in this codebase reads `dryRawAdc`/`wetRawAdc` yet. Storing the calibration ahead of having a consumer mirrors the same reasoning already accepted for `SoilSensorProperties` itself in ADR-018.

## Consequences

- Calibration data for all 5 sensors now lives in `application.yml` alongside their plant assignments — one config block, one source of truth per sensor, rather than splitting sensor facts across two places.
- The raw ADC readings that produced these reference values remain permanently in `soil_moisture_reading` unchanged — calibration is a config-level interpretation layer, never a rewrite of stored facts, satisfying the spec's "preserve raw readings permanently so calibration logic can be improved later without losing the original measurement."
- If a probe is later replaced or drifts enough to need recalibration, updating two YAML values and redeploying is the whole procedure — no migration, no entity change.
- The ~1600-unit dry-wet spread was consistent across all 5 probes (this is a cheap, mass-produced sensor type, so consistency wasn't guaranteed) — a reasonable sign these probes behave predictably enough that a simple linear percentage mapping, whenever it's eventually built, is a reasonable model rather than requiring per-probe curve-fitting.

## Related / superseded decisions

Extends ADR-018's `SoilSensorProperties`/`SoilSensorAssignment` rather than introducing a new config section or entity. Does not implement anything from spec §9 (twin/assessment/UI exposure), which remains a distinct, separately-scoped future increment.
