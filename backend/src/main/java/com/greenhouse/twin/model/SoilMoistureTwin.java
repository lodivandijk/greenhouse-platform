package com.greenhouse.twin.model;

import com.greenhouse.twin.status.FreshnessStatus;

import java.time.Instant;

// A facts-only soil moisture reading in the twin: the raw ADC value and how
// stale it is. Deliberately NO moisture index and no crop linkage here -
// converting raw to an index requires calibration, and deciding whether an
// index is "too dry" requires a crop's profile. Both are interpretation and
// live in the assessment layer (ADR-021, and the twin's facts-only rule in
// ARCHITECTURE_PARADIGM.md section 6).
public record SoilMoistureTwin(
        String sensorId,
        Integer rawAdc,
        Instant observedAt,
        Long ageSeconds,
        FreshnessStatus freshness
) {
}
