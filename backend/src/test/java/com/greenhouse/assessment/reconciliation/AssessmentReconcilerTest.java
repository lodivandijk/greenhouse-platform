package com.greenhouse.assessment.reconciliation;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.AssessmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentReconcilerTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-26T12:00:00Z");
    private static final String GREENHOUSE_ID = "greenhouse-01";

    @Mock
    private AssessmentRepository assessmentRepository;

    private final AssessmentMapper assessmentMapper = new AssessmentMapper();
    private AssessmentReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new AssessmentReconciler(assessmentRepository, assessmentMapper);
        when(assessmentRepository.save(any(AssessmentEntity.class))).thenAnswer(invocation -> {
            AssessmentEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(100L);
            }
            return entity;
        });
    }

    private static AssessmentFinding finding(AssessmentCode code, String correlationKey, AssessmentSeverity severity) {
        return new AssessmentFinding(
                code, severity, AssessmentScopeType.ZONE, "zone-main", GREENHOUSE_ID, "zone-main", null,
                "message", Map.of("k", "v"), "rule-id", 1, correlationKey
        );
    }

    private static AssessmentEntity activeEntity(
            Long id, String correlationKey, AssessmentCode code, AssessmentSeverity severity, Instant firstDetectedAt
    ) {
        return new AssessmentEntity(
                id, correlationKey, GREENHOUSE_ID, "zone-main", null,
                AssessmentScopeType.ZONE, "zone-main", code, severity, AssessmentStatus.ACTIVE,
                "message", Map.of("k", "v"), "rule-id", 1,
                firstDetectedAt, firstDetectedAt, firstDetectedAt, null, firstDetectedAt, firstDetectedAt
        );
    }

    @Test
    void newCondition_raisesOneActiveAssessment() {
        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of());

        AssessmentFinding finding = finding(AssessmentCode.TEMPERATURE_ABOVE_LIMIT, "key-1", AssessmentSeverity.WARNING);

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(finding), EVALUATED_AT);

        assertThat(changes.raised()).hasSize(1);
        assertThat(changes.updated()).isEmpty();
        assertThat(changes.resolved()).isEmpty();
        assertThat(changes.raised().get(0).status()).isEqualTo(AssessmentStatus.ACTIVE);
        assertThat(changes.raised().get(0).firstDetectedAt()).isEqualTo(EVALUATED_AT);
    }

    @Test
    void persistentCondition_updatesSameRecordNoDuplicate() {
        Instant firstDetected = EVALUATED_AT.minusSeconds(120);
        AssessmentEntity existing = activeEntity(1L, "key-1", AssessmentCode.TEMPERATURE_ABOVE_LIMIT, AssessmentSeverity.WARNING, firstDetected);

        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of(existing));

        AssessmentFinding finding = finding(AssessmentCode.TEMPERATURE_ABOVE_LIMIT, "key-1", AssessmentSeverity.WARNING);

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(finding), EVALUATED_AT);

        assertThat(changes.raised()).isEmpty();
        assertThat(changes.updated()).hasSize(1);
        assertThat(changes.resolved()).isEmpty();
        assertThat(changes.updated().get(0).id()).isEqualTo(1L);
        assertThat(changes.updated().get(0).firstDetectedAt()).isEqualTo(firstDetected);
        assertThat(changes.updated().get(0).lastEvaluatedAt()).isEqualTo(EVALUATED_AT);
    }

    @Test
    void resolvedCondition_resolvesRecordWhenNoMatchingFinding() {
        Instant firstDetected = EVALUATED_AT.minusSeconds(120);
        AssessmentEntity existing = activeEntity(1L, "key-1", AssessmentCode.TEMPERATURE_ABOVE_LIMIT, AssessmentSeverity.WARNING, firstDetected);

        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of(existing));

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(), EVALUATED_AT);

        assertThat(changes.raised()).isEmpty();
        assertThat(changes.updated()).isEmpty();
        assertThat(changes.resolved()).hasSize(1);
        assertThat(changes.resolved().get(0).status()).isEqualTo(AssessmentStatus.RESOLVED);
        assertThat(changes.resolved().get(0).resolvedAt()).isEqualTo(EVALUATED_AT);
    }

    @Test
    void recurrence_resolvedHistoryDoesNotBlockNewActiveRecord() {
        // No active record currently exists for this correlation key (a previous occurrence
        // was already resolved, and is therefore excluded from the active-only query) -
        // a fresh finding must raise a brand-new active record rather than being blocked.
        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of());

        AssessmentFinding finding = finding(AssessmentCode.TEMPERATURE_ABOVE_LIMIT, "key-1", AssessmentSeverity.WARNING);

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(finding), EVALUATED_AT);

        assertThat(changes.raised()).hasSize(1);
        assertThat(changes.raised().get(0).status()).isEqualTo(AssessmentStatus.ACTIVE);
    }

    @Test
    void multipleFindings_produceIndependentAssessmentRecords() {
        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of());

        AssessmentFinding findingA = finding(AssessmentCode.TEMPERATURE_ABOVE_LIMIT, "key-1", AssessmentSeverity.WARNING);
        AssessmentFinding findingB = finding(AssessmentCode.HUMIDITY_BELOW_LIMIT, "key-2", AssessmentSeverity.WARNING);

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(findingA, findingB), EVALUATED_AT);

        assertThat(changes.raised()).hasSize(2);
    }

    @Test
    void severityChange_updatesSameAssessment() {
        Instant firstDetected = EVALUATED_AT.minusSeconds(120);
        AssessmentEntity existing = activeEntity(1L, "key-1", AssessmentCode.DEVICE_OFFLINE, AssessmentSeverity.WARNING, firstDetected);

        when(assessmentRepository.findAllByGreenhouseIdAndStatus(GREENHOUSE_ID, AssessmentStatus.ACTIVE))
                .thenReturn(List.of(existing));

        AssessmentFinding finding = finding(AssessmentCode.DEVICE_OFFLINE, "key-1", AssessmentSeverity.CRITICAL);

        AssessmentChanges changes = reconciler.reconcile(GREENHOUSE_ID, List.of(finding), EVALUATED_AT);

        assertThat(changes.updated()).hasSize(1);
        assertThat(changes.updated().get(0).id()).isEqualTo(1L);
        assertThat(changes.updated().get(0).severity()).isEqualTo(AssessmentSeverity.CRITICAL);
    }
}
