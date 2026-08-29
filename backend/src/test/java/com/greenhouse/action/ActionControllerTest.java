package com.greenhouse.action;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private Long createCrop() throws Exception {
        String body = mockMvc.perform(post("/api/v1/crops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"species\":\"Strawberry\",\"locationId\":\"pot-2\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void recordAction_returnsPersistedActionWithDefaults() throws Exception {
        Long cropId = createCrop();

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + cropId + ",\"type\":\"WATER\",\"quantity\":100.0,\"unit\":\"ml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WATER"))
                .andExpect(jsonPath("$.performedBy").value("HUMAN"))
                .andExpect(jsonPath("$.performedAt").exists());
    }

    @Test
    void recordAction_quantityWithoutUnit_returnsBadRequest() throws Exception {
        Long cropId = createCrop();

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + cropId + ",\"type\":\"WATER\",\"quantity\":100.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordAction_unknownCrop_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":999999,\"type\":\"WATER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listActions_filteredByCrop_returnsInReverseChronologicalOrder() throws Exception {
        Long cropId = createCrop();

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + cropId + ",\"type\":\"WATER\",\"performedAt\":\"2026-08-10T08:00:00Z\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + cropId + ",\"type\":\"PRUNE\",\"performedAt\":\"2026-08-12T08:00:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/actions").param("cropId", String.valueOf(cropId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("PRUNE"))
                .andExpect(jsonPath("$[1].type").value("WATER"));
    }

    @Test
    void getAction_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/actions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Action not found: 999999"));
    }

    @Test
    void cropHistory_includesActions() throws Exception {
        Long cropId = createCrop();
        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + cropId + ",\"type\":\"WATER\",\"quantity\":100.0,\"unit\":\"ml\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/crops/" + cropId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0].type").value("WATER"));
    }
}
