-- Carries the real measured calibration values from ADR-020 forward into the
-- versioned tables introduced by ADR-022, rather than discarding them and
-- re-measuring. Values were collected 2026-08-29: each probe held stable in
-- air, then fully immersed in water, over multiple confirmed-stable 60s
-- observation cycles.
--
-- Calibration is per-probe and crop-independent, so it seeds unconditionally.
INSERT INTO sensor_calibration (
    sensor_id, version, dry_reference_raw, wet_reference_raw,
    calibrated_at, calibrated_by, method, notes
)
VALUES
    ('soil-01', 1, 2814, 1181, TIMESTAMPTZ '2026-08-29 16:40:00+01', 'migration-seed', 'air-and-immersion-2026-08-29', 'Seeded from ADR-020 measured values.'),
    ('soil-02', 1, 2706, 1121, TIMESTAMPTZ '2026-08-29 16:40:00+01', 'migration-seed', 'air-and-immersion-2026-08-29', 'Seeded from ADR-020 measured values.'),
    ('soil-03', 1, 2707, 1105, TIMESTAMPTZ '2026-08-29 16:40:00+01', 'migration-seed', 'air-and-immersion-2026-08-29', 'Seeded from ADR-020 measured values.'),
    ('soil-04', 1, 2794, 1179, TIMESTAMPTZ '2026-08-29 16:40:00+01', 'migration-seed', 'air-and-immersion-2026-08-29', 'Seeded from ADR-020 measured values.'),
    ('soil-05', 1, 2717, 1134, TIMESTAMPTZ '2026-08-29 16:40:00+01', 'migration-seed', 'air-and-immersion-2026-08-29', 'Seeded from ADR-020 measured values.');

-- Crop assignment can only be seeded where the crop actually exists. The
-- production database has the six herb crops; a fresh development or CI
-- database has an empty crop table, where this correctly inserts nothing
-- rather than failing on a foreign key to a hardcoded id.
--
-- Matching on species is a one-time bootstrap convenience only. From here on
-- the crop_id foreign key is authoritative and the species string is never
-- consulted again for sensor resolution (that fragile coupling is precisely
-- what ADR-022 removes). DISTINCT ON keeps this deterministic if a species
-- ever has more than one non-ended crop.
INSERT INTO crop_sensor_assignment (sensor_id, crop_id, version, assigned_at, assigned_by, notes)
SELECT
    mapping.sensor_id,
    mapping.crop_id,
    1,
    TIMESTAMPTZ '2026-08-29 16:40:00+01',
    'migration-seed',
    'Seeded from ADR-018/ADR-020 configuration.'
FROM (
    SELECT DISTINCT ON (seed.sensor_id)
        seed.sensor_id,
        crop.id AS crop_id
    FROM (
        VALUES
            ('soil-01', 'Basil'),
            ('soil-02', 'Thyme'),
            ('soil-03', 'Mint'),
            ('soil-04', 'Sage'),
            ('soil-05', 'Oregano')
    ) AS seed(sensor_id, species)
    JOIN crop ON crop.species = seed.species AND crop.status <> 'ENDED'
    ORDER BY seed.sensor_id, crop.id
) AS mapping;

-- Tarragon deliberately receives no assignment row. Absence is how "no sensor
-- assigned" is represented, which is what lets the assessment engine report
-- NO_SENSOR_ASSIGNED as an honest distinct state rather than inferring
-- dryness from missing data.
