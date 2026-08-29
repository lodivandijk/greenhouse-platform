-- Crop-aware assessments record which crop they concern and which versioned
-- profile/calibration produced them, so that later recalibration or a profile
-- change never silently rewrites the meaning of historical evidence (ADR-021).
--
-- All nullable: the four pre-existing zone/device-level rules have no crop,
-- profile, or calibration context and continue to work unchanged.
--
-- No enum type changes needed - scope_type and code are VARCHAR columns
-- (@Enumerated(STRING)), so the new CROP scope type and the seven new
-- crop-aware codes are Java-side enum additions only.
ALTER TABLE assessment
    ADD COLUMN crop_id                     BIGINT REFERENCES crop(id),
    ADD COLUMN monitoring_profile_id       BIGINT REFERENCES crop_monitoring_profile(id),
    ADD COLUMN monitoring_profile_version  INTEGER,
    ADD COLUMN calibration_id              BIGINT REFERENCES sensor_calibration(id),
    ADD COLUMN calibration_version         INTEGER;

CREATE INDEX idx_assessment_crop ON assessment(crop_id) WHERE crop_id IS NOT NULL;
