package com.greenhouse.crop;

import java.time.Instant;

public record CreateCropRequest(
        String species,
        String variety,
        String locationId,
        Instant plantedAt,
        String notes
) {
}
