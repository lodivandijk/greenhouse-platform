package com.greenhouse.crop;

import java.time.Instant;

public record HarvestResponse(
        Long id,
        Long cropId,
        Instant harvestedAt,
        Double quantity,
        HarvestUnit unit,
        String notes,
        Instant createdAt
) {
}
