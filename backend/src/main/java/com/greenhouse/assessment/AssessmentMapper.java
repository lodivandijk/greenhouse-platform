package com.greenhouse.assessment;

import org.springframework.stereotype.Component;

@Component
public class AssessmentMapper {

    public AssessmentResponse toResponse(AssessmentEntity entity) {
        return new AssessmentResponse(
                entity.getId(),
                entity.getCorrelationKey(),
                entity.getGreenhouseId(),
                entity.getZoneId(),
                entity.getDeviceId(),
                entity.getScopeType(),
                entity.getScopeId(),
                entity.getCode(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getEvidence(),
                entity.getRuleId(),
                entity.getRuleVersion(),
                entity.getFirstDetectedAt(),
                entity.getLastDetectedAt(),
                entity.getLastEvaluatedAt(),
                entity.getResolvedAt()
        );
    }
}
