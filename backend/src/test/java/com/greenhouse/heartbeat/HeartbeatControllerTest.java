package com.greenhouse.heartbeat;

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
class HeartbeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsAndReturnsHeartbeat() throws Exception {
        String deviceId = "test-device-" + UUID.randomUUID();

        HeartbeatRequest request = new HeartbeatRequest(
                deviceId,
                "0.1.0",
                "192.168.1.68",
                -52,
                120L
        );

        mockMvc.perform(post("/api/heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value(deviceId))
                .andExpect(jsonPath("$.heartbeatCount").value(1))
                .andExpect(jsonPath("$.online").value(true));

        mockMvc.perform(get("/api/devices/" + deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void secondHeartbeatIncrementsCountAndKeepsFirstSeenAt() throws Exception {
        String deviceId = "test-device-" + UUID.randomUUID();
        HeartbeatRequest request = new HeartbeatRequest(deviceId, "0.1.0", "192.168.1.68", -52, 120L);

        mockMvc.perform(post("/api/v1/heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.heartbeatCount").value(1));

        mockMvc.perform(post("/api/v1/heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.heartbeatCount").value(2))
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }
}
