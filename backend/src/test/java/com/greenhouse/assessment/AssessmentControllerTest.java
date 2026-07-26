package com.greenhouse.assessment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "greenhouse.evaluation.enabled=false")
@AutoConfigureMockMvc
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultsToActiveAssessments() throws Exception {
        mockMvc.perform(get("/api/v1/assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.assessments").isArray());
    }

    @Test
    void explicitActiveStatusWorks() throws Exception {
        mockMvc.perform(get("/api/v1/assessments").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessments").isArray());
    }

    @Test
    void invalidStatusValue_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/assessments").param("status", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest());
    }
}
