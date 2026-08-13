package com.greenhouse.mcp;

import com.greenhouse.goal.GoalService;
import com.greenhouse.goal.GoalType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoalTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoalTools.class);

    private final GoalService goalService;
    private final McpJsonMapper mcpJsonMapper;

    public GoalTools(GoalService goalService, McpJsonMapper mcpJsonMapper) {
        this.goalService = goalService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification listGoalsTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("list_goals")
                .description("Lists every goal ever set for a crop, including completed and cancelled ones, "
                        + "in the order they were created.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop, from list_crops.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () ->
                        goalService.listGoalsByCrop(McpToolSupport.requireLong(request.arguments(), "cropId"))))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification createGoalTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("create_goal")
                .description("Records what outcome the user wants for a crop - their intent, not an executable "
                        + "control instruction. Use this when the user states an objective for a specific crop. "
                        + "goalType must be one of MAXIMISE_LONG_TERM_YIELD, MAXIMISE_FOLIAGE, MAXIMISE_FLOWERING, "
                        + "MAXIMISE_FRUIT_QUALITY, or OTHER (OTHER requires a description). Always pass the user's "
                        + "own words in sourceInstruction when available, even if you also set a structured "
                        + "goalType.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop this goal is for.\"},"
                        + "\"goalType\":{\"type\":\"string\",\"description\":\"One of MAXIMISE_LONG_TERM_YIELD, MAXIMISE_FOLIAGE, MAXIMISE_FLOWERING, MAXIMISE_FRUIT_QUALITY, OTHER.\"},"
                        + "\"description\":{\"type\":\"string\",\"description\":\"Required when goalType is OTHER; optional otherwise.\"},"
                        + "\"sourceInstruction\":{\"type\":\"string\",\"description\":\"The user's own words describing what they want, verbatim where possible.\"},"
                        + "\"priority\":{\"type\":\"integer\",\"description\":\"Optional relative priority.\"}"
                        + "},\"required\":[\"cropId\",\"goalType\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () -> {
                    var arguments = request.arguments();
                    return goalService.createGoal(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.requireEnum(arguments, "goalType", GoalType.class),
                            McpToolSupport.optionalString(arguments, "description"),
                            McpToolSupport.optionalString(arguments, "sourceInstruction"),
                            McpToolSupport.optionalInteger(arguments, "priority")
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification deleteGoalTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("delete_goal")
                .description("Permanently deletes a single goal record - use this to correct a goal that was "
                        + "recorded in error. To simply stop pursuing a goal without erasing that it existed, "
                        + "prefer leaving it as-is or discussing changing its status instead; there is no "
                        + "update_goal tool yet.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"goalId\":{\"type\":\"integer\",\"description\":\"The numeric id of the goal to delete, from list_goals.\"}"
                        + "},\"required\":[\"goalId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () ->
                        goalService.deleteGoal(McpToolSupport.requireLong(request.arguments(), "goalId"))))
                .build();
    }
}
