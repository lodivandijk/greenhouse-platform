package com.greenhouse.assessment;

import com.greenhouse.assessment.reconciliation.AssessmentReconciler;
import com.greenhouse.assessment.rule.AssessmentRule;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-26T12:00:00Z");
    private static final GreenhouseTwin EMPTY_TWIN =
            new GreenhouseTwin("greenhouse-01", "Home Greenhouse", null, EVALUATED_AT, null, List.of());

    @Mock
    private AssessmentReconciler assessmentReconciler;

    private static AssessmentRule stubRule(String ruleId, String correlationKey) {
        return new AssessmentRule() {
            @Override
            public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
                return List.of(new AssessmentFinding(
                        AssessmentCode.TEMPERATURE_ABOVE_LIMIT, AssessmentSeverity.WARNING,
                        AssessmentScopeType.ZONE, "zone-main", "greenhouse-01", "zone-main", null,
                        "message", Map.of(), ruleId, 1, correlationKey
                ));
            }

            @Override
            public String ruleId() {
                return ruleId;
            }

            @Override
            public int ruleVersion() {
                return 1;
            }
        };
    }

    @Test
    void duplicateCorrelationKeysAcrossRules_failClearly() {
        String sharedKey = "greenhouse-01:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT";
        AssessmentService service = new AssessmentService(
                List.of(stubRule("rule-a", sharedKey), stubRule("rule-b", sharedKey)),
                assessmentReconciler
        );

        assertThatThrownBy(() -> service.assessAndReconcile(EMPTY_TWIN, EVALUATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate correlation key")
                .hasMessageContaining("rule-a")
                .hasMessageContaining("rule-b");
    }

    @Test
    void noDuplicates_reconcilesWithAllFindings() {
        AssessmentService service = new AssessmentService(
                List.of(stubRule("rule-a", "key-a"), stubRule("rule-b", "key-b")),
                assessmentReconciler
        );
        AssessmentChanges expected = new AssessmentChanges(List.of(), List.of(), List.of());
        when(assessmentReconciler.reconcile(
                eq("greenhouse-01"),
                argThat(findings -> findings.size() == 2),
                eq(EVALUATED_AT)
        )).thenReturn(expected);

        AssessmentChanges result = service.assessAndReconcile(EMPTY_TWIN, EVALUATED_AT);

        assertThat(result).isSameAs(expected);
        verify(assessmentReconciler).reconcile(eq("greenhouse-01"), anyList(), eq(EVALUATED_AT));
    }
}
