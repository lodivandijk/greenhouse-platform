package com.greenhouse.crop;

import java.time.Instant;

public record CropResponse(
        Long id,
        String species,
        String variety,
        String locationId,
        Instant plantedAt,
        Instant endedAt,
        CropStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
