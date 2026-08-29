package com.greenhouse.assessment;

import java.util.Map;

// A rule's output for one condition in one evaluation cycle.
//
// cropId/monitoringProfile*/calibration* are populated only by crop-aware
// rules. The four zone/device rules keep using the 12-argument constructor
// below, which defaults them to null - so adding crop awareness required no
// change to any existing rule.
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
        String correlationKey,
        Long cropId,
        Long monitoringProfileId,
        Integer monitoringProfileVersion,
        Long calibrationId,
        Integer calibrationVersion
) {

    public AssessmentFinding(
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
        this(code, severity, scopeType, scopeId, greenhouseId, zoneId, deviceId, message, evidence,
                ruleId, ruleVersion, correlationKey, null, null, null, null, null);
    }
}
