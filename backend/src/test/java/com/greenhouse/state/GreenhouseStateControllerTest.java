package com.greenhouse.state;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class GreenhouseStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTwinAndActiveAssessments() throws Exception {
        mockMvc.perform(get("/api/v1/state"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.twin.greenhouseId").value("greenhouse-01"))
                .andExpect(jsonPath("$.assessments").isArray());
    }

    @Test
    void isReadOnly_doesNotModifyAssessments() throws Exception {
        String payload = "{\"deviceId\":\"greenhouse-esp32-01\",\"temperatureCelsius\":40.0,"
                + "\"humidityPercent\":58.0,\"pressureHpa\":1013.5}";

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessments").isEmpty());

        mockMvc.perform(get("/api/v1/assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessments").isEmpty());
    }
}
