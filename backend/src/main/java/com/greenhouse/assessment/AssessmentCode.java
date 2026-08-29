package com.greenhouse.assessment;

public enum AssessmentCode {
    // Zone/device-level conditions, evaluated against the greenhouse-wide
    // operating limits in TwinProperties.
    TEMPERATURE_BELOW_LIMIT,
    TEMPERATURE_ABOVE_LIMIT,
    HUMIDITY_BELOW_LIMIT,
    HUMIDITY_ABOVE_LIMIT,
    OBSERVATION_STALE,
    DEVICE_OFFLINE,

    // Crop-level conditions, evaluated against each crop's own monitoring
    // profile. "PREFERRED" rather than "LIMIT" is deliberate: these are
    // growing preferences, not damage thresholds, and a short excursion is
    // not crop damage (ADR-021).
    CROP_TEMPERATURE_BELOW_PREFERRED,
    CROP_TEMPERATURE_ABOVE_PREFERRED,
    CROP_SOIL_MOISTURE_LOW,
    CROP_SOIL_MOISTURE_HIGH,
    CROP_SENSOR_DATA_STALE,
    CROP_SENSOR_CALIBRATION_REQUIRED,
    CROP_SENSOR_NOT_ASSIGNED
}
