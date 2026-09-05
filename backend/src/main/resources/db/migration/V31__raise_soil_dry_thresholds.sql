-- Raises the dry thresholds at which a crop is reported as needing water.
--
-- The mint (crop 10) wilted visibly while its moisture index sat at 43.6 and
-- its threshold was 30, so nothing was ever raised. Its index had declined
-- steadily for six days - 78, 74, 67, 62, 50, 46, 44 - without once crossing
-- the line. The thresholds were set from a spec table, never validated against
-- a real plant in this greenhouse; this is the first time reality has been
-- allowed to correct them.
--
--   EVENLY_MOIST         (basil, mint)                    30 -> 50
--   DRY_BETWEEN_WATERING (thyme, sage, oregano, tarragon) 15 -> 30
--
-- At 50, the mint would have been flagged on 2026-09-03, two days before the
-- wilting became visible.
--
-- A new version per crop, superseding the old rather than editing it, so the
-- assessments already raised under the previous thresholds stay explicable
-- (ADR-021). Matched by strategy rather than by crop id, so this applies
-- correctly on the Pi and inserts nothing on an empty dev/CI database.

-- The previous version must be disabled before the new one is inserted:
-- uq_crop_monitoring_profile_enabled permits one enabled profile per crop and
-- is checked per statement, not deferred to commit.
CREATE TEMPORARY TABLE profile_threshold_update AS
SELECT
    p.id AS previous_id,
    p.crop_id,
    p.version + 1 AS next_version,
    CASE p.soil_moisture_strategy
        WHEN 'EVENLY_MOIST'         THEN 50.0
        WHEN 'DRY_BETWEEN_WATERING' THEN 30.0
    END AS new_dry_threshold
FROM crop_monitoring_profile p
WHERE p.enabled = TRUE
  AND p.soil_moisture_strategy IN ('EVENLY_MOIST', 'DRY_BETWEEN_WATERING')
  -- Only where the value actually changes; an unchanged version would be noise
  -- in the history and would misdate the decision.
  AND (p.soil_dry_threshold_index IS DISTINCT FROM
        CASE p.soil_moisture_strategy
            WHEN 'EVENLY_MOIST'         THEN 50.0
            WHEN 'DRY_BETWEEN_WATERING' THEN 30.0
        END);

UPDATE crop_monitoring_profile
SET enabled = FALSE
WHERE id IN (SELECT previous_id FROM profile_threshold_update);

INSERT INTO crop_monitoring_profile (
    crop_id, version,
    preferred_temperature_min_celsius, preferred_temperature_max_celsius,
    temperature_excursion_seconds, temperature_recovery_seconds,
    soil_moisture_strategy, soil_dry_threshold_index, soil_wet_threshold_index,
    soil_monitoring_mode,
    enabled, created_at, created_by, source_notes, supersedes_profile_id
)
SELECT
    previous.crop_id, update_row.next_version,
    previous.preferred_temperature_min_celsius, previous.preferred_temperature_max_celsius,
    previous.temperature_excursion_seconds, previous.temperature_recovery_seconds,
    previous.soil_moisture_strategy,
    update_row.new_dry_threshold,
    previous.soil_wet_threshold_index,
    -- Carried forward deliberately: Tarragon is MANUAL and must stay MANUAL.
    -- Its threshold is updated anyway so that returning it to SENSOR one day
    -- does not quietly restore a stale value (ADR-024).
    previous.soil_monitoring_mode,
    TRUE, now(), 'migration-seed',
    'Dry threshold raised after the mint wilted at moisture index 43.6 with a '
        || 'threshold of 30. Moisture-loving crops now 50, Mediterranean crops now 30.',
    previous.id
FROM profile_threshold_update AS update_row
JOIN crop_monitoring_profile previous ON previous.id = update_row.previous_id;

DROP TABLE profile_threshold_update;
