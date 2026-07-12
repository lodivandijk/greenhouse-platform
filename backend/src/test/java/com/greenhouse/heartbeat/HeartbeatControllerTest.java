package com.greenhouse.heartbeat;

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
class HeartbeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsAndReturnsHeartbeat() throws Exception {
        HeartbeatRequest request = new HeartbeatRequest(
                "greenhouse-esp32-01",
                "0.1.0",
                "192.168.1.68",
                -52,
                120L
        );

        mockMvc.perform(post("/api/heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value("greenhouse-esp32-01"))
                .andExpect(jsonPath("$.heartbeatCount").value(1))
                .andExpect(jsonPath("$.online").value(true));

        mockMvc.perform(get("/api/devices/greenhouse-esp32-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("greenhouse-esp32-01"));
    }
}
