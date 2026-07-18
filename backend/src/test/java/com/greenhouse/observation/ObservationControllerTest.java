package com.greenhouse.observation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsAndReturnsObservation() throws Exception {
        ObservationRequest request = new ObservationRequest(
                "greenhouse-esp32-01",
                22.4,
                55.1,
                1013.2
        );

        mockMvc.perform(post("/api/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value("greenhouse-esp32-01"))
                .andExpect(jsonPath("$.temperatureCelsius").value(22.4));

        mockMvc.perform(get("/api/observations/greenhouse-esp32-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("greenhouse-esp32-01"));
    }

    @Test
    void rejectsBlankDeviceId() throws Exception {
        String invalidPayload = "{\"deviceId\":\"\",\"temperatureCelsius\":22.4,"
                + "\"humidityPercent\":55.1,\"pressureHpa\":1013.2}";

        mockMvc.perform(post("/api/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownDevice() throws Exception {
        mockMvc.perform(get("/api/observations/unknown-device"))
                .andExpect(status().isNotFound());
    }
}
