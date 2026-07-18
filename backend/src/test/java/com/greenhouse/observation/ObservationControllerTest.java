package com.greenhouse.observation;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueDeviceId() {
        return "test-device-" + UUID.randomUUID();
    }

    @Test
    void recordsAndReturnsObservation() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(deviceId, 22.4, 55.1, 1013.2);

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value(deviceId))
                .andExpect(jsonPath("$.temperatureCelsius").value(22.4));

        mockMvc.perform(get("/api/v1/observations/" + deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void legacyUnversionedPathStillWorks() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(deviceId, 20.0, 50.0, 1000.0);

        mockMvc.perform(post("/api/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/observations/" + deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void listsAllObservationsIncludingTheNewOne() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(deviceId, 21.0, 60.0, 1010.0);

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/observations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void returnsGlobalLatestObservation() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(deviceId, 19.5, 45.0, 1005.0);

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/observations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void rejectsBlankDeviceId() throws Exception {
        String invalidPayload = "{\"deviceId\":\"\",\"temperatureCelsius\":22.4,"
                + "\"humidityPercent\":55.1,\"pressureHpa\":1013.2}";

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownDevice() throws Exception {
        mockMvc.perform(get("/api/v1/observations/" + uniqueDeviceId()))
                .andExpect(status().isNotFound());
    }
}
