package com.greenhouse.assessment;

import java.time.Instant;
import java.util.Map;

public record AssessmentResponse(
        Long id,
        String correlationKey,
        String greenhouseId,
        String zoneId,
        String deviceId,
        AssessmentScopeType scopeType,
        String scopeId,
        AssessmentCode code,
        AssessmentSeverity severity,
        AssessmentStatus status,
        String message,
        Map<String, Object> evidence,
        String ruleId,
        int ruleVersion,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        Instant lastEvaluatedAt,
        Instant resolvedAt
) {
}
