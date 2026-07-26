package com.greenhouse.assessment.reconciliation;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.assessment.AssessmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AssessmentReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssessmentReconciler.class);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentMapper assessmentMapper;

    public AssessmentReconciler(AssessmentRepository assessmentRepository, AssessmentMapper assessmentMapper) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentMapper = assessmentMapper;
    }

    @Transactional
    public AssessmentChanges reconcile(String greenhouseId, List<AssessmentFinding> findings, Instant evaluatedAt) {
        List<AssessmentEntity> activeAssessments =
                assessmentRepository.findAllByGreenhouseIdAndStatus(greenhouseId, AssessmentStatus.ACTIVE);

        Map<String, AssessmentEntity> activeByCorrelationKey = activeAssessments.stream()
                .collect(Collectors.toMap(AssessmentEntity::getCorrelationKey, Function.identity()));

        Map<String, AssessmentFinding> findingsByCorrelationKey = findings.stream()
                .collect(Collectors.toMap(AssessmentFinding::correlationKey, Function.identity()));

        List<AssessmentResponse> raised = new ArrayList<>();
        List<AssessmentResponse> updated = new ArrayList<>();
        List<AssessmentResponse> resolved = new ArrayList<>();

        for (AssessmentFinding finding : findings) {
            AssessmentEntity existing = activeByCorrelationKey.get(finding.correlationKey());

            if (existing == null) {
                AssessmentEntity created = assessmentRepository.save(raise(finding, evaluatedAt));
                raised.add(assessmentMapper.toResponse(created));
                LOGGER.info(
                        "Assessment raised: correlationKey={} code={} severity={}",
                        created.getCorrelationKey(), created.getCode(), created.getSeverity()
                );
            } else {
                boolean severityChanged = existing.getSeverity() != finding.severity();
                applyUpdate(existing, finding, evaluatedAt);
                AssessmentEntity saved = assessmentRepository.save(existing);
                updated.add(assessmentMapper.toResponse(saved));
                if (severityChanged) {
                    LOGGER.info(
                            "Assessment severity changed: correlationKey={} newSeverity={}",
                            saved.getCorrelationKey(), saved.getSeverity()
                    );
                }
            }
        }

        for (AssessmentEntity active : activeAssessments) {
            if (!findingsByCorrelationKey.containsKey(active.getCorrelationKey())) {
                applyResolve(active, evaluatedAt);
                AssessmentEntity saved = assessmentRepository.save(active);
                resolved.add(assessmentMapper.toResponse(saved));
                LOGGER.info("Assessment resolved: correlationKey={}", saved.getCorrelationKey());
            }
        }

        return new AssessmentChanges(raised, updated, resolved);
    }

    private AssessmentEntity raise(AssessmentFinding finding, Instant evaluatedAt) {
        return new AssessmentEntity(
                null,
                finding.correlationKey(),
                finding.greenhouseId(),
                finding.zoneId(),
                finding.deviceId(),
                finding.scopeType(),
                finding.scopeId(),
                finding.code(),
                finding.severity(),
                AssessmentStatus.ACTIVE,
                finding.message(),
                finding.evidence(),
                finding.ruleId(),
                finding.ruleVersion(),
                evaluatedAt,
                evaluatedAt,
                evaluatedAt,
                null,
                evaluatedAt,
                evaluatedAt
        );
    }

    private void applyUpdate(AssessmentEntity existing, AssessmentFinding finding, Instant evaluatedAt) {
        existing.setSeverity(finding.severity());
        existing.setMessage(finding.message());
        existing.setEvidence(finding.evidence());
        existing.setRuleId(finding.ruleId());
        existing.setRuleVersion(finding.ruleVersion());
        existing.setLastDetectedAt(evaluatedAt);
        existing.setLastEvaluatedAt(evaluatedAt);
        existing.setUpdatedAt(evaluatedAt);
    }

    private void applyResolve(AssessmentEntity active, Instant evaluatedAt) {
        active.setStatus(AssessmentStatus.RESOLVED);
        active.setResolvedAt(evaluatedAt);
        active.setLastEvaluatedAt(evaluatedAt);
        active.setUpdatedAt(evaluatedAt);
    }
}
