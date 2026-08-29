-- Initial monitoring profiles for the six herb crops, from the preferred
-- ranges and soil behaviour given in the Daily Crop Status spec section 4.
--
-- These are preferred GROWING ranges, not damage thresholds. The excursion
-- (3600s) and recovery (1800s) durations are the spec's stated defaults: a
-- condition must hold for an hour before it becomes a task, and must stay
-- clear for half an hour before the task closes, which is what stops a brief
-- blip from generating work.
--
-- Soil thresholds are expressed on the 0-100 moisture index (0 = at this
-- probe's dry reference, 100 = at its wet reference), not on raw ADC, so they
-- are comparable across probes with different raw ranges. Moisture-loving
-- herbs get a higher dry threshold (they should be watered sooner);
-- dry-leaning herbs get a lower dry threshold and a wet ceiling, since sitting
-- wet is their real risk.
--
-- Matched by species rather than hardcoded crop ids so this applies correctly
-- on the production database and inserts nothing on an empty dev/CI database,
-- exactly as the V13 assignment seed does.
INSERT INTO crop_monitoring_profile (
    crop_id, version,
    preferred_temperature_min_celsius, preferred_temperature_max_celsius,
    temperature_excursion_seconds, temperature_recovery_seconds,
    soil_moisture_strategy, soil_dry_threshold_index, soil_wet_threshold_index,
    enabled, created_at, created_by, source_notes
)
SELECT
    mapping.crop_id, 1,
    mapping.temp_min, mapping.temp_max,
    3600, 1800,
    mapping.strategy, mapping.dry_threshold, mapping.wet_threshold,
    TRUE, now(), 'migration-seed',
    'Seeded from the Daily Crop Status and Human Feedback Loop v1 spec, section 4.'
FROM (
    SELECT DISTINCT ON (seed.species)
        crop.id AS crop_id,
        seed.temp_min,
        seed.temp_max,
        seed.strategy,
        seed.dry_threshold,
        seed.wet_threshold
    FROM (
        VALUES
            -- species,     min,  max,  strategy,               dry,  wet
            ('Basil',       18.0, 27.0, 'EVENLY_MOIST',         30.0, NULL),
            ('Thyme',       15.0, 25.0, 'DRY_BETWEEN_WATERING', 15.0, 80.0),
            ('Mint',        15.0, 24.0, 'EVENLY_MOIST',         30.0, NULL),
            ('Sage',        15.0, 25.0, 'DRY_BETWEEN_WATERING', 15.0, 75.0),
            ('Oregano',     15.0, 26.0, 'DRY_BETWEEN_WATERING', 15.0, 75.0),
            ('Tarragon',    15.0, 24.0, 'DRY_BETWEEN_WATERING', 15.0, 75.0)
    ) AS seed(species, temp_min, temp_max, strategy, dry_threshold, wet_threshold)
    JOIN crop ON crop.species = seed.species AND crop.status <> 'ENDED'
    ORDER BY seed.species, crop.id
) AS mapping;
