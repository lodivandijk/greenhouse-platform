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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
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

    @Test
    void acceptsPayloadWithOneSoilSensorAndItIsRetrievable() throws Exception {
        String deviceId = uniqueDeviceId();
        String sensorId = "test-sensor-" + UUID.randomUUID();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.8, 68.4, 1014.2,
                List.of(new SoilMoistureReadingRequest(sensorId, 2870))
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/soil-moisture-readings").param("sensorId", sensorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorId").value(sensorId))
                .andExpect(jsonPath("$[0].rawAdc").value(2870))
                .andExpect(jsonPath("$[0].deviceId").value(deviceId));
    }

    @Test
    void acceptsPayloadWithAnEmptySoilMoistureArray() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(deviceId, 21.0, 60.0, 1010.0, List.of());

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsRawAdcAboveFourThousandNinetyFive() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.0, 60.0, 1010.0,
                List.of(new SoilMoistureReadingRequest("test-sensor-" + UUID.randomUUID(), 4096))
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeRawAdc() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.0, 60.0, 1010.0,
                List.of(new SoilMoistureReadingRequest("test-sensor-" + UUID.randomUUID(), -1))
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankSensorId() throws Exception {
        String deviceId = uniqueDeviceId();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.0, 60.0, 1010.0,
                List.of(new SoilMoistureReadingRequest("", 2870))
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateSensorIdsInOnePayload() throws Exception {
        String deviceId = uniqueDeviceId();
        String sensorId = "test-sensor-" + UUID.randomUUID();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.0, 60.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(sensorId, 2870),
                        new SoilMoistureReadingRequest(sensorId, 2900)
                )
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void persistsThreeSoilSensorsFromOneCycleWithoutOverwriting() throws Exception {
        String deviceId = uniqueDeviceId();
        String sensor1 = "test-sensor-" + UUID.randomUUID();
        String sensor2 = "test-sensor-" + UUID.randomUUID();
        String sensor3 = "test-sensor-" + UUID.randomUUID();
        ObservationRequest request = new ObservationRequest(
                deviceId, 21.0, 60.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(sensor1, 2870),
                        new SoilMoistureReadingRequest(sensor2, 2915),
                        new SoilMoistureReadingRequest(sensor3, 2842)
                )
        );

        mockMvc.perform(post("/api/v1/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/soil-moisture-readings").param("sensorId", sensor1))
                .andExpect(jsonPath("$[0].rawAdc").value(2870));
        mockMvc.perform(get("/api/v1/soil-moisture-readings").param("sensorId", sensor2))
                .andExpect(jsonPath("$[0].rawAdc").value(2915));
        mockMvc.perform(get("/api/v1/soil-moisture-readings").param("sensorId", sensor3))
                .andExpect(jsonPath("$[0].rawAdc").value(2842));
    }
}
