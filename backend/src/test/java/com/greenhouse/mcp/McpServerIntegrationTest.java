package com.greenhouse.mcp;

import com.greenhouse.action.ActionRepository;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.HarvestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

// Uses a real embedded server and a real HTTP client (see DashboardStaticResourceTest
// for why), which means writes made through MCP tool calls genuinely persist to the
// dev database and are NOT rolled back by @Transactional. Every crop this test class
// creates is tracked and deleted in @AfterEach.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "greenhouse.evaluation.enabled=false",
                "greenhouse.mcp.auth-token=test-mcp-token-12345"
        }
)
class McpServerIntegrationTest {

    private static final String TOKEN = "test-mcp-token-12345";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private HarvestRepository harvestRepository;

    @Autowired
    private ActionRepository actionRepository;

    private final List<Long> createdCropIds = new ArrayList<>();

    @AfterEach
    void cleanUpCreatedCrops() {
        for (Long cropId : createdCropIds) {
            actionRepository.deleteAll(actionRepository.findAllByCropIdOrderByPerformedAtDesc(cropId));
        }
        cropRepository.deleteAllById(createdCropIds);
        createdCropIds.clear();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/mcp";
    }

    private String initializeSession() throws Exception {
        HttpResponse<String> response = McpTestSupport.post(
                baseUrl(), TOKEN, null, McpTestSupport.initializeRequestBody());
        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue("Mcp-Session-Id").orElseThrow();

        HttpResponse<String> initialized = McpTestSupport.post(
                baseUrl(), TOKEN, sessionId, McpTestSupport.initializedNotificationBody());
        assertThat(initialized.statusCode()).isEqualTo(202);

        return sessionId;
    }

    @Test
    void toolDiscovery_listsExpectedToolsWithSchemas() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> response = McpTestSupport.post(
                baseUrl(), TOKEN, sessionId, McpTestSupport.toolsListRequestBody());

        JsonNode body = McpTestSupport.parseJsonRpcBody(jsonMapper, response.body());
        JsonNode tools = body.path("result").path("tools");

        var toolNames = StreamSupport.stream(tools.spliterator(), false)
                .map(t -> t.path("name").asString())
                .toList();

        assertThat(toolNames).contains(
                "get_greenhouse_state", "list_crops", "get_crop", "get_crop_history", "list_goals",
                "create_crop", "update_crop", "create_goal", "record_harvest", "record_crop_observation",
                "delete_crop", "delete_goal", "delete_harvest", "delete_crop_observation",
                "record_action", "list_actions"
        );

        JsonNode getCropTool = StreamSupport.stream(tools.spliterator(), false)
                .filter(t -> "get_crop".equals(t.path("name").asString()))
                .findFirst().orElseThrow();
        assertThat(getCropTool.path("inputSchema").path("required").toString()).contains("cropId");
    }

    @Test
    void toolCall_getGreenhouseState_returnsRealTrustedState() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> response = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "get_greenhouse_state", Map.of()));

        JsonNode body = McpTestSupport.parseJsonRpcBody(jsonMapper, response.body());
        String stateText = body.path("result").path("content").get(0).path("text").asString();
        JsonNode state = jsonMapper.readTree(stateText);

        assertThat(state.path("twin").path("greenhouseId").asString()).isEqualTo("greenhouse-01");
        assertThat(body.path("result").path("isError").asBoolean(false)).isFalse();
    }

    @Test
    void toolCall_createCropThenListCrops_roundTripsThroughRealDomainService() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> createResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Basil-mcp-test", "location", "planter-mcp-test"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createResponse.body());
        String createdText = createBody.path("result").path("content").get(0).path("text").asString();
        JsonNode created = jsonMapper.readTree(createdText);
        long cropId = created.path("id").asLong();
        createdCropIds.add(cropId);

        assertThat(created.path("status").asString()).isEqualTo("ESTABLISHING");

        HttpResponse<String> listResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "list_crops", Map.of()));
        JsonNode listBody = McpTestSupport.parseJsonRpcBody(jsonMapper, listResponse.body());
        String listedText = listBody.path("result").path("content").get(0).path("text").asString();

        assertThat(listedText).contains("\"id\":" + cropId);
        assertThat(listedText).contains("Basil-mcp-test");
    }

    @Test
    void toolCall_getCropWithUnknownId_returnsCleanDomainErrorNotStackTrace() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> response = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "get_crop", Map.of("cropId", 987654321)));

        JsonNode body = McpTestSupport.parseJsonRpcBody(jsonMapper, response.body());
        assertThat(body.path("result").path("isError").asBoolean()).isTrue();
        String text = body.path("result").path("content").get(0).path("text").asString();
        assertThat(text).isEqualTo("Crop not found: 987654321");
        assertThat(text).doesNotContain("Exception");
    }

    @Test
    void toolCall_createGoalWithInvalidGoalType_returnsCleanValidationError() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> createCropResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Basil-goal-test", "location", "planter-goal-test"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createCropResponse.body());
        long cropId = jsonMapper.readTree(createBody.path("result").path("content").get(0).path("text").asString())
                .path("id").asLong();
        createdCropIds.add(cropId);

        HttpResponse<String> response = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_goal", Map.of(
                        "cropId", cropId, "goalType", "MAXIMISE_WORLD_DOMINATION"
                )));

        JsonNode body = McpTestSupport.parseJsonRpcBody(jsonMapper, response.body());
        assertThat(body.path("result").path("isError").asBoolean()).isTrue();
        assertThat(body.path("result").path("content").get(0).path("text").asString())
                .contains("Invalid goalType");
    }

    @Test
    void toolCall_deleteCrop_emptyCrop_succeeds() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> createResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Basil-delete-test", "location", "planter-delete-test"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createResponse.body());
        long cropId = jsonMapper.readTree(createBody.path("result").path("content").get(0).path("text").asString())
                .path("id").asLong();
        createdCropIds.add(cropId);

        HttpResponse<String> deleteResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "delete_crop", Map.of("cropId", cropId)));
        JsonNode deleteBody = McpTestSupport.parseJsonRpcBody(jsonMapper, deleteResponse.body());
        assertThat(deleteBody.path("result").path("isError").asBoolean(false)).isFalse();

        HttpResponse<String> getResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "get_crop", Map.of("cropId", cropId)));
        JsonNode getBody = McpTestSupport.parseJsonRpcBody(jsonMapper, getResponse.body());
        assertThat(getBody.path("result").path("isError").asBoolean()).isTrue();
    }

    @Test
    void toolCall_deleteCrop_withRecordedHarvest_isBlockedWithCleanError() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> createResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Basil-delete-blocked-test", "location", "planter-delete-blocked-test"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createResponse.body());
        long cropId = jsonMapper.readTree(createBody.path("result").path("content").get(0).path("text").asString())
                .path("id").asLong();
        createdCropIds.add(cropId);

        McpTestSupport.post(baseUrl(), TOKEN, sessionId, McpTestSupport.toolsCallRequestBody(
                jsonMapper, "record_harvest", Map.of("cropId", cropId, "quantity", 50.0, "unit", "GRAMS")));

        HttpResponse<String> deleteResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "delete_crop", Map.of("cropId", cropId)));
        JsonNode deleteBody = McpTestSupport.parseJsonRpcBody(jsonMapper, deleteResponse.body());
        assertThat(deleteBody.path("result").path("isError").asBoolean()).isTrue();
        assertThat(deleteBody.path("result").path("content").get(0).path("text").asString())
                .contains("cannot be deleted");

        // Clean up the harvest directly so the crop can be cleaned up normally in @AfterEach.
        harvestRepository.deleteAll(harvestRepository.findAllByCropIdOrderByHarvestedAtAsc(cropId));
    }

    @Test
    void toolCall_recordActionThenListActions_roundTripsThroughRealDomainService() throws Exception {
        String sessionId = initializeSession();

        HttpResponse<String> createResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Strawberry-action-test", "location", "pot-2"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createResponse.body());
        long cropId = jsonMapper.readTree(createBody.path("result").path("content").get(0).path("text").asString())
                .path("id").asLong();
        createdCropIds.add(cropId);

        HttpResponse<String> actionResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "record_action", Map.of(
                        "cropId", cropId, "type", "WATER", "quantity", 100.0, "unit", "ml"
                )));
        JsonNode actionBody = McpTestSupport.parseJsonRpcBody(jsonMapper, actionResponse.body());
        assertThat(actionBody.path("result").path("isError").asBoolean(false)).isFalse();
        String actionText = actionBody.path("result").path("content").get(0).path("text").asString();
        assertThat(actionText).contains("\"type\":\"WATER\"");
        assertThat(actionText).contains("\"performedBy\":\"HUMAN\"");

        HttpResponse<String> listResponse = McpTestSupport.post(baseUrl(), TOKEN, sessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "list_actions", Map.of("cropId", cropId)));
        JsonNode listBody = McpTestSupport.parseJsonRpcBody(jsonMapper, listResponse.body());
        String listedText = listBody.path("result").path("content").get(0).path("text").asString();

        assertThat(listedText).contains("\"type\":\"WATER\"");
        assertThat(listedText).contains("\"cropId\":" + cropId);
    }

    // This is the milestone's actual Definition of Done, proven directly rather than assumed:
    // an action recorded in one MCP session must be retrievable from a completely separate
    // session (its own initialize handshake, its own session id) - proving persistence lives
    // in PostgreSQL, not in any conversation state.
    @Test
    void action_recordedInOneSession_isRetrievableFromABrandNewSession() throws Exception {
        String firstSessionId = initializeSession();

        HttpResponse<String> createResponse = McpTestSupport.post(baseUrl(), TOKEN, firstSessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "create_crop", Map.of(
                        "species", "Strawberry-cross-session-test", "location", "pot-3"
                )));
        JsonNode createBody = McpTestSupport.parseJsonRpcBody(jsonMapper, createResponse.body());
        long cropId = jsonMapper.readTree(createBody.path("result").path("content").get(0).path("text").asString())
                .path("id").asLong();
        createdCropIds.add(cropId);

        McpTestSupport.post(baseUrl(), TOKEN, firstSessionId, McpTestSupport.toolsCallRequestBody(
                jsonMapper, "record_action", Map.of(
                        "cropId", cropId, "type", "WATER", "quantity", 100.0, "unit", "ml"
                )));

        // A brand new session - no relationship to the one that recorded the action above.
        String secondSessionId = initializeSession();
        assertThat(secondSessionId).isNotEqualTo(firstSessionId);

        HttpResponse<String> listResponse = McpTestSupport.post(baseUrl(), TOKEN, secondSessionId,
                McpTestSupport.toolsCallRequestBody(jsonMapper, "list_actions", Map.of("cropId", cropId)));
        JsonNode listBody = McpTestSupport.parseJsonRpcBody(jsonMapper, listResponse.body());
        String listedText = listBody.path("result").path("content").get(0).path("text").asString();

        assertThat(listedText).contains("\"type\":\"WATER\"");
        assertThat(listedText).contains("\"unit\":\"ml\"");
    }
}
