package com.greenhouse.action;

import java.time.Instant;

public record ActionResponse(
        Long id,
        Long cropId,
        ActionType type,
        String description,
        Double quantity,
        String unit,
        Instant performedAt,
        ActionPerformedBy performedBy,
        Instant createdAt
) {
}
