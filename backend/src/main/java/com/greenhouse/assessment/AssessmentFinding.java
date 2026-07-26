package com.greenhouse.assessment;

import java.util.Map;

public record AssessmentFinding(
        AssessmentCode code,
        AssessmentSeverity severity,
        AssessmentScopeType scopeType,
        String scopeId,
        String greenhouseId,
        String zoneId,
        String deviceId,
        String message,
        Map<String, Object> evidence,
        String ruleId,
        int ruleVersion,
        String correlationKey
) {
}
