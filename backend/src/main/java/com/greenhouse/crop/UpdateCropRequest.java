package com.greenhouse.crop;

import java.time.Instant;

public record UpdateCropRequest(
        String variety,
        String locationId,
        CropStatus status,
        String notes,
        Instant endedAt
) {
}
