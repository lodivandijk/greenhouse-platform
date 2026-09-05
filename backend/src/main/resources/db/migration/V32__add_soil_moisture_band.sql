-- Replaces hand-picked per-crop threshold numbers with a named band that says
-- how thirsty the crop is judged to be (ADR-028).
--
--   HIGH   dry 50, no wet ceiling  - wants consistently moist soil
--   MEDIUM dry 40, wet 85          - moderate, even moisture
--   LOW    dry 30, wet 75          - drought-tolerant, dries out between waterings
--
-- The resolved numbers are still stored on each version, so the assessment rule
-- keeps reading plain thresholds and a historical assessment still shows the
-- exact values that produced it, even after a band's definition later changes.
--
-- Adding a band (VERY_HIGH, say) means a new enum constant plus a migration
-- assigning it - a deliberate change, which is the point of using bands at all.
ALTER TABLE crop_monitoring_profile
    ADD COLUMN soil_moisture_band VARCHAR(50);

-- Historical versions are deliberately left NULL. They predate the concept, and
-- inventing a band for thresholds that were never chosen as one would be
-- fiction in a table whose whole job is to explain past assessments.

CREATE TEMPORARY TABLE profile_band_assignment AS
SELECT
    p.id AS previous_id,
    p.crop_id,
    p.version + 1 AS next_version,
    CASE p.soil_moisture_strategy
        WHEN 'EVENLY_MOIST'         THEN 'HIGH'
        WHEN 'DRY_BETWEEN_WATERING' THEN 'LOW'
    END AS band
FROM crop_monitoring_profile p
WHERE p.enabled = TRUE
  AND p.soil_moisture_strategy IN ('EVENLY_MOIST', 'DRY_BETWEEN_WATERING');

-- Disabled before the replacement is inserted:
-- uq_crop_monitoring_profile_enabled permits one enabled profile per crop and
-- is checked per statement, not deferred to commit.
UPDATE crop_monitoring_profile
SET enabled = FALSE
WHERE id IN (SELECT previous_id FROM profile_band_assignment);

INSERT INTO crop_monitoring_profile (
    crop_id, version,
    preferred_temperature_min_celsius, preferred_temperature_max_celsius,
    temperature_excursion_seconds, temperature_recovery_seconds,
    soil_moisture_strategy, soil_dry_threshold_index, soil_wet_threshold_index,
    soil_moisture_band, soil_monitoring_mode,
    enabled, created_at, created_by, source_notes, supersedes_profile_id
)
SELECT
    previous.crop_id, assignment.next_version,
    previous.preferred_temperature_min_celsius, previous.preferred_temperature_max_celsius,
    previous.temperature_excursion_seconds, previous.temperature_recovery_seconds,
    previous.soil_moisture_strategy,
    -- Resolved from the band, in one place, rather than per crop by hand.
    CASE assignment.band WHEN 'HIGH' THEN 50.0 WHEN 'MEDIUM' THEN 40.0 WHEN 'LOW' THEN 30.0 END,
    CASE assignment.band WHEN 'HIGH' THEN NULL WHEN 'MEDIUM' THEN 85.0 WHEN 'LOW' THEN 75.0 END,
    assignment.band,
    -- Carried forward: Tarragon is MANUAL and must stay MANUAL (ADR-024).
    previous.soil_monitoring_mode,
    TRUE, now(), 'migration-seed',
    'Assigned a soil moisture band. Moisture-loving crops are HIGH, Mediterranean '
        || 'crops are LOW. This aligns thyme''s wet ceiling with the other '
        || 'Mediterranean herbs (80 to 75); every other crop keeps the thresholds '
        || 'it already had.',
    previous.id
FROM profile_band_assignment AS assignment
JOIN crop_monitoring_profile previous ON previous.id = assignment.previous_id;

DROP TABLE profile_band_assignment;
