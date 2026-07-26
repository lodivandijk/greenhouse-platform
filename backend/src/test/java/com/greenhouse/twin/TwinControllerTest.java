package com.greenhouse.twin;

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

@SpringBootTest(properties = "greenhouse.evaluation.enabled=false")
@AutoConfigureMockMvc
@Transactional
class TwinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsCurrentTwinWithFreshObservation() throws Exception {
        String payload = "{\"deviceId\":\"greenhouse-esp32-01\",\"temperatureCelsius\":22.5,"
                + "\"humidityPercent\":58.0,\"pressureHpa\":1013.5}";

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/twin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.greenhouseId").value("greenhouse-01"))
                .andExpect(jsonPath("$.status").value("NORMAL"))
                .andExpect(jsonPath("$.zones[0].zoneId").value("zone-main"))
                .andExpect(jsonPath("$.zones[0].environment.temperatureCelsius").value(22.5))
                .andExpect(jsonPath("$.zones[0].assessment.level").value("NORMAL"))
                .andExpect(jsonPath("$.zones[0].dataQuality.freshness").value("CURRENT"))
                .andExpect(jsonPath("$.zones[0].devices[0].status").value("ONLINE"));
    }
}
