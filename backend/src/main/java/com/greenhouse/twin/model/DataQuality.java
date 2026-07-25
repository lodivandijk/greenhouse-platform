package com.greenhouse.twin.model;

import com.greenhouse.twin.status.FreshnessStatus;

import java.time.Instant;

public record DataQuality(
        FreshnessStatus freshness,
        Long ageSeconds,
        Instant observedAt,
        boolean complete
) {
}
