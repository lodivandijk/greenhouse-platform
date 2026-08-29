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
        List<ZoneTwin> zones,
        // Flat rather than nested under a zone: soil sensors are not zone-scoped
        // in configuration today, and forcing them into the zone model would be
        // a larger restructure than this increment needs.
        List<SoilMoistureTwin> soilMoisture
) {
}
