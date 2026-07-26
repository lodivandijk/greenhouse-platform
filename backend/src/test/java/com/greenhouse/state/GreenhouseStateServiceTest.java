package com.greenhouse.state;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentQueryService;
import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.twin.TwinService;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.status.TwinStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GreenhouseStateServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Mock
    private TwinService twinService;

    @Mock
    private AssessmentQueryService assessmentQueryService;

    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    void composesTwinAndActiveAssessments() {
        GreenhouseTwin twin = new GreenhouseTwin("greenhouse-01", "Home Greenhouse", TwinStatus.NORMAL, FIXED_NOW, FIXED_NOW, List.of());
        AssessmentResponse activeAssessment = new AssessmentResponse(
                1L, "greenhouse-01:DEVICE:device-1:DEVICE_OFFLINE", "greenhouse-01", null, "device-1",
                AssessmentScopeType.DEVICE, "device-1", AssessmentCode.DEVICE_OFFLINE, AssessmentSeverity.CRITICAL,
                AssessmentStatus.ACTIVE, "message", Map.of(), "device-availability", 1,
                FIXED_NOW, FIXED_NOW, FIXED_NOW, null
        );

        when(twinService.getCurrentTwin()).thenReturn(twin);
        when(assessmentQueryService.getActiveAssessments()).thenReturn(List.of(activeAssessment));

        GreenhouseStateService service = new GreenhouseStateService(twinService, assessmentQueryService, fixedClock);

        GreenhouseStateResponse state = service.getCurrentState();

        assertThat(state.generatedAt()).isEqualTo(FIXED_NOW);
        assertThat(state.twin()).isEqualTo(twin);
        assertThat(state.assessments()).containsExactly(activeAssessment);
    }

    @Test
    void doesNotTriggerReconciliation() {
        GreenhouseTwin twin = new GreenhouseTwin("greenhouse-01", "Home Greenhouse", TwinStatus.NORMAL, FIXED_NOW, FIXED_NOW, List.of());
        when(twinService.getCurrentTwin()).thenReturn(twin);
        when(assessmentQueryService.getActiveAssessments()).thenReturn(List.of());

        GreenhouseStateService service = new GreenhouseStateService(twinService, assessmentQueryService, fixedClock);
        service.getCurrentState();

        verifyNoMoreInteractions(twinService, assessmentQueryService);
    }
}
