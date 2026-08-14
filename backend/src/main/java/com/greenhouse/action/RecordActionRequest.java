package com.greenhouse.action;

import java.time.Instant;

public record RecordActionRequest(
        Long cropId,
        ActionType type,
        String description,
        Double quantity,
        String unit,
        Instant performedAt,
        ActionPerformedBy performedBy
) {
}
