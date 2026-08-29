package com.greenhouse.assessment;

import com.greenhouse.evaluation.GreenhouseEvaluationCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the raise -> stays-active -> resolve lifecycle, driven against a
 * dedicated test device (overridden via properties) so it never touches the real
 * greenhouse-esp32-01 device's data. The real scheduler is disabled here so the
 * coordinator is only invoked explicitly, keeping the lifecycle deterministic.
 */
@SpringBootTest(properties = {
        "greenhouse.twin.zones[0].zone-id=zone-main",
        "greenhouse.twin.zones[0].name=Main Greenhouse",
        "greenhouse.twin.zones[0].device-ids[0]=lifecycle-test-device",
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AssessmentLifecycleTest {

    private static final String CORRELATION_KEY = "greenhouse-01:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GreenhouseEvaluationCoordinator coordinator;

    @Autowired
    private ObjectMapper objectMapper;

    private void postObservation(double temperatureCelsius) throws Exception {
        String payload = String.format(
                Locale.ROOT,
                "{\"deviceId\":\"lifecycle-test-device\",\"temperatureCelsius\":%.1f,\"humidityPercent\":50.0,\"pressureHpa\":1012.0}",
                temperatureCelsius
        );
        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());
    }

    private AssessmentListResponse getAssessments(String queryStatus) throws Exception {
        String content = mockMvc.perform(get("/api/v1/assessments").param("status", queryStatus))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(content, AssessmentListResponse.class);
    }

    private static long countByCorrelationKey(List<AssessmentResponse> assessments, String correlationKey) {
        return assessments.stream().filter(a -> a.correlationKey().equals(correlationKey)).count();
    }

    @Test
    void fullAssessmentLifecycle_raiseStayActiveThenResolve() throws Exception {
        // 1-2. High temperature observation, then run the coordinator.
        postObservation(38.2);
        coordinator.evaluate();

        // 3. TEMPERATURE_ABOVE_LIMIT is ACTIVE, exactly once.
        AssessmentListResponse afterFirstRun = getAssessments("ACTIVE");
        assertThat(countByCorrelationKey(afterFirstRun.assessments(), CORRELATION_KEY)).isEqualTo(1);

        // 4-5. Run the coordinator again with the same state - still exactly one active record.
        coordinator.evaluate();
        AssessmentListResponse afterSecondRun = getAssessments("ACTIVE");
        assertThat(countByCorrelationKey(afterSecondRun.assessments(), CORRELATION_KEY)).isEqualTo(1);

        // 6-7. Normal-temperature observation, then run the coordinator.
        postObservation(22.0);
        coordinator.evaluate();

        // 8. The assessment is RESOLVED.
        AssessmentListResponse resolved = getAssessments("RESOLVED");
        assertThat(countByCorrelationKey(resolved.assessments(), CORRELATION_KEY)).isEqualTo(1);

        // 9. It no longer appears in the active list (i.e. /api/v1/state, once it composes
        // active assessments in checkpoint 2, would show no active high-temperature warning).
        AssessmentListResponse active = getAssessments("ACTIVE");
        assertThat(countByCorrelationKey(active.assessments(), CORRELATION_KEY)).isZero();
    }
}
