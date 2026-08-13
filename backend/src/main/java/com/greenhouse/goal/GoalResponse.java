package com.greenhouse.goal;

import java.time.Instant;
import java.util.Map;

public record GoalResponse(
        Long id,
        Long cropId,
        GoalType goalType,
        String description,
        GoalStatus status,
        Integer priority,
        String sourceInstruction,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
