-- Distinguishes a crop whose probe is missing from one that deliberately has
-- none (ADR-024). Without this the engine reads both as a data-quality problem
-- and raises CROP_SENSOR_NOT_ASSIGNED forever against a crop that is being
-- tended perfectly well by eye.
--
-- Defaults to SENSOR so every existing profile keeps exactly its current
-- behaviour; only an explicit change opts a crop out.
ALTER TABLE crop_monitoring_profile
    ADD COLUMN soil_monitoring_mode VARCHAR(50) NOT NULL DEFAULT 'SENSOR';

-- Tarragon has no probe by choice - only five were wired, and the sixth was a
-- decision rather than an omission.
--
-- This is a new profile VERSION, not an edit: the version-1 row is retained,
-- disabled, so the assessments it already produced remain explicable. Matched
-- by species rather than a hardcoded crop id, so it applies correctly on the
-- Pi and inserts nothing on an empty dev/CI database, exactly as V13 and V27 do.
--
-- The previous version must be disabled before the new one is inserted:
-- uq_crop_monitoring_profile_enabled permits only one enabled profile per crop
-- and is checked per statement, not deferred to commit.
UPDATE crop_monitoring_profile
SET enabled = FALSE
WHERE enabled = TRUE
  AND crop_id IN (
      SELECT id FROM crop WHERE species = 'Tarragon' AND status <> 'ENDED'
  );

INSERT INTO crop_monitoring_profile (
    crop_id, version,
    preferred_temperature_min_celsius, preferred_temperature_max_celsius,
    temperature_excursion_seconds, temperature_recovery_seconds,
    soil_moisture_strategy, soil_dry_threshold_index, soil_wet_threshold_index,
    soil_monitoring_mode,
    enabled, created_at, created_by, source_notes, supersedes_profile_id
)
SELECT
    previous.crop_id, previous.version + 1,
    previous.preferred_temperature_min_celsius, previous.preferred_temperature_max_celsius,
    previous.temperature_excursion_seconds, previous.temperature_recovery_seconds,
    -- Every other setting is carried forward unchanged; only the mode differs.
    previous.soil_moisture_strategy, previous.soil_dry_threshold_index, previous.soil_wet_threshold_index,
    'MANUAL',
    TRUE, now(), 'migration-seed',
    'Tarragon is deliberately monitored by hand; no soil probe is wired for it. '
        || 'Recorded as structured configuration so the engine stops reporting a '
        || 'chosen absence as a data-quality fault (ADR-024).',
    previous.id
FROM crop_monitoring_profile previous
WHERE previous.crop_id IN (
        SELECT id FROM crop WHERE species = 'Tarragon' AND status <> 'ENDED'
      )
  AND previous.version = (
        SELECT MAX(version) FROM crop_monitoring_profile latest
        WHERE latest.crop_id = previous.crop_id
      );
