package com.greenhouse.crop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "greenhouse.evaluation.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CropControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private Long createCrop() throws Exception {
        String payload = "{\"species\":\"Basil\",\"variety\":\"Genovese\",\"locationId\":\"planter-02\","
                + "\"plantedAt\":\"2026-08-01T12:00:00Z\",\"notes\":\"first crop\"}";

        String body = mockMvc.perform(post("/api/v1/crops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void createCrop_returnsPersistedCrop() throws Exception {
        mockMvc.perform(post("/api/v1/crops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"species\":\"Basil\",\"locationId\":\"planter-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.species").value("Basil"))
                .andExpect(jsonPath("$.status").value("ESTABLISHING"));
    }

    @Test
    void createCrop_missingSpecies_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/crops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"planter-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCrop_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/crops/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Crop not found: 999999"));
    }

    @Test
    void fullLifecycle_createUpdateHarvestObservationGoalHistory() throws Exception {
        Long cropId = createCrop();

        mockMvc.perform(get("/api/v1/crops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + cropId + ")]").exists());

        mockMvc.perform(patch("/api/v1/crops/" + cropId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRODUCTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRODUCTIVE"));

        mockMvc.perform(post("/api/v1/crops/" + cropId + "/harvests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":180.0,\"unit\":\"GRAMS\",\"harvestedAt\":\"2026-08-13T09:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(180.0));

        mockMvc.perform(post("/api/v1/crops/" + cropId + "/observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"PLANT_HEALTH\",\"valueType\":\"TEXT\",\"textValue\":\"healthy\","
                                + "\"source\":\"HUMAN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textValue").value("healthy"));

        mockMvc.perform(post("/api/v1/crops/" + cropId + "/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalType\":\"MAXIMISE_FOLIAGE\","
                                + "\"sourceInstruction\":\"I want as much usable foliage as possible.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/crops/" + cropId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crop.id").value(cropId))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.harvests.length()").value(1))
                .andExpect(jsonPath("$.observations.length()").value(1));
    }
}
