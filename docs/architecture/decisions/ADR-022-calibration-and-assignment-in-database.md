# ADR-022: Soil Sensor Calibration and Crop Assignment Move to the Database

**Status:** Accepted
**Date:** 2026-08-29

## Context

ADR-018 established that soil-sensor-to-plant assignment belongs in Pi-side configuration rather than ESP32 firmware, so re-assigning a probe never requires a reflash. ADR-020 then stored measured dry/wet calibration reference points in that same configuration block (`greenhouse.soil-sensors.assignments` in `application.yml`, bound via `SoilSensorProperties`). Both were accepted on 2026-08-29, and nothing consumed either value at the time — the config existed because the spec required the assignment to be represented *somewhere* other than firmware, ahead of having a real consumer.

The care-loop work (ADR-021) is that consumer, and it surfaces two requirements YAML config genuinely cannot meet:

1. **Resolution "as of evaluation time."** A crop-soil-moisture assessment must record *which* calibration version produced its moisture index, so that recalibrating a probe later never silently changes the meaning of historical assessments. A redeployed config file has no version, no history, and no way to answer "what was true when this assessment was raised."
2. **Provenance.** The spec's `SensorCalibration` model requires `calibratedBy`, `method`, `calibratedAt`, and `supersedesCalibrationId`. A YAML edit has no natural author — it's whoever last redeployed. These are fields that only make sense for a record written through a domain service by an identified actor.

There is also a modelling gap: `SoilSensorProperties.plant` is a bare display string (`"Basil"`), not a `Crop` foreign key. The only thing linking sensor `soil-01` to crop id 8 today is an informal string match between that config value and `Crop.species` — fragile, and unusable for the care loop, which needs a real crop reference.

## Decision

Move both concerns into versioned database tables:

- **`sensor_calibration`** — per physical probe: `sensorId`, `version`, `dryReferenceRaw`, `wetReferenceRaw`, `calibratedAt`, `calibratedBy`, `method`, `notes`, `supersedesCalibrationId`. Calibration belongs to the *probe*, not the crop — a probe moved to a different pot keeps its calibration.
- **`crop_sensor_assignment`** — which crop a probe currently serves: `sensorId`, `cropId` (a real FK), `version`, `assignedAt`, `assignedBy`, `supersedesAssignmentId`, `validTo`. Separate table from calibration precisely because sensor identity and crop assignment are independent facts that change independently (ADR-018's core principle, now properly modelled rather than implied).

A data-only seed migration carries the real measurements from ADR-020 forward as version-1 rows (`soil-01` dry=2814/wet=1181, `soil-02` 2706/1121, `soil-03` 2707/1105, `soil-04` 2794/1179, `soil-05` 2717/1134), with `calibratedBy='migration-seed'` and `method='air-and-immersion-2026-08-29'`. The measurements are genuinely hard-won physical data — they become the historical record rather than being discarded and re-measured. Assignments seed to the confirmed live crop ids (8=Basil, 9=Thyme, 10=Mint, 11=Sage, 12=Oregano). **Tarragon (crop 13) gets no assignment row at all** — absence is how "no sensor" is represented, not a null-calibration placeholder, which is what lets the assessment engine report `NO_SENSOR_ASSIGNED` as a distinct, honest state.

`SoilSensorProperties` and its `application.yml` block are **left in place but become bootstrap-only** — no runtime code path reads them after this ships. Its startup validation (rejecting duplicate sensor ids, rejecting `wet >= dry`) is harmless and still catches config typos, so removing it would be churn for no benefit. It should be deleted in a later cleanup increment once the DB-backed path has proven itself in production.

This supersedes **only the storage-location decision** in ADR-020. ADR-018's underlying principle — that assignment and policy live on the Pi, never in firmware — is unchanged and in fact strengthened: it's now in the Pi's database rather than the Pi's config file, still nowhere near the ESP32.

## Consequences

- Recalibrating a probe becomes an append (new version row, `supersedesCalibrationId` pointing at the old one) rather than a config edit + redeploy, and historical assessments keep referencing the calibration version that was actually used.
- The `soil_moisture_reading` table is untouched — no FK to `crop_sensor_assignment`, preserving ADR-018's deliberate "raw ingestion must keep working regardless of domain state" rationale. Assignment is resolved by joining on the `sensorId` string at query time.
- The informal `plant`-string-to-`Crop.species` match disappears, replaced by a real `cropId` FK — which also means a crop can be renamed, or two crops of the same species can coexist, without breaking sensor resolution.
- Two sources of truth exist transiently (YAML and DB) until `SoilSensorProperties` is deleted. Mitigated by making the DB unambiguously authoritative at runtime from day one — nothing reads the YAML values, so they cannot drift into disagreement in any way that affects behavior.
- ADR-020 was accepted the same day this supersedes it. That's not churn for its own sake: ADR-020 correctly captured "store the calibration somewhere sensible now, no consumer exists yet," and the consumer arriving hours later changed the requirements in a way that a config file genuinely cannot satisfy. Per the repo's own convention, that's a supersession, not a rewrite — ADR-020 stays exactly as written, as the record of what was true when it was decided.

## Related / superseded decisions

Supersedes ADR-020's calibration-storage-location decision. Preserves and strengthens ADR-018's firmware/Pi boundary. Enables ADR-021's care-loop model, which requires evaluation-time resolution of both calibration and assignment.
