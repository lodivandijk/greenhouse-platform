package com.greenhouse.twin.model;

import com.greenhouse.twin.status.TwinStatus;

import java.time.Instant;
import java.util.List;

public record GreenhouseTwin(
        String greenhouseId,
        String name,
        TwinStatus status,
        Instant generatedAt,
        Instant lastUpdatedAt,
        List<ZoneTwin> zones
) {
}
