package com.greenhouse.mcp;

import com.greenhouse.action.ActionPerformedBy;
import com.greenhouse.action.ActionService;
import com.greenhouse.action.ActionType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActionTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionTools.class);

    private final ActionService actionService;
    private final McpJsonMapper mcpJsonMapper;

    public ActionTools(ActionService actionService, McpJsonMapper mcpJsonMapper) {
        this.actionService = actionService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordActionTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_action")
                .description("Records real agricultural work performed on a crop - watering, feeding, pruning, "
                        + "pollinating, moving, planting. This is distinct from record_crop_observation (what was "
                        + "seen/measured) and record_harvest (what was produced): use record_action for what a "
                        + "person (or in future, an automated system) actually did. type must be one of WATER, "
                        + "FEED, PRUNE, POLLINATE, MOVE, PLANT, or OTHER if nothing fits (put detail in "
                        + "description). If quantity is given, unit must be given too, e.g. quantity=100, "
                        + "unit='ml'. performedAt defaults to now and performedBy defaults to HUMAN if omitted.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop this action concerns.\"},"
                        + "\"type\":{\"type\":\"string\",\"description\":\"One of WATER, FEED, PRUNE, POLLINATE, MOVE, PLANT, OTHER.\"},"
                        + "\"description\":{\"type\":\"string\",\"description\":\"Optional free-text detail, e.g. 'Removed first runner.'\"},"
                        + "\"quantity\":{\"type\":\"number\",\"description\":\"Optional numeric quantity, e.g. 100. Requires unit if set.\"},"
                        + "\"unit\":{\"type\":\"string\",\"description\":\"Optional unit for quantity, e.g. 'ml', 'g', 'cm'.\"},"
                        + "\"performedAt\":{\"type\":\"string\",\"description\":\"ISO-8601 timestamp of when it actually happened. Defaults to now if omitted.\"},"
                        + "\"performedBy\":{\"type\":\"string\",\"description\":\"One of HUMAN, AGENT, AUTOMATION, SYSTEM. Defaults to HUMAN.\"}"
                        + "},\"required\":[\"cropId\",\"type\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () -> {
                    var arguments = request.arguments();
                    return actionService.recordAction(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.requireEnum(arguments, "type", ActionType.class),
                            McpToolSupport.optionalString(arguments, "description"),
                            McpToolSupport.optionalDouble(arguments, "quantity"),
                            McpToolSupport.optionalString(arguments, "unit"),
                            McpToolSupport.optionalInstant(arguments, "performedAt"),
                            McpToolSupport.optionalEnum(arguments, "performedBy", ActionPerformedBy.class)
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification listActionsTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("list_actions")
                .description("Lists work previously performed on a crop (watering, feeding, pruning, etc.), most "
                        + "recent first. Use this to reconstruct what has already been done before deciding what "
                        + "to do next, or to answer questions like 'what have I done to this crop recently?'")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop, from list_crops.\"},"
                        + "\"limit\":{\"type\":\"integer\",\"description\":\"Optional maximum number of actions to return.\"},"
                        + "\"since\":{\"type\":\"string\",\"description\":\"Optional ISO-8601 timestamp - only actions performed after this are returned.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () -> {
                    var arguments = request.arguments();
                    return actionService.listActions(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.optionalInteger(arguments, "limit"),
                            McpToolSupport.optionalInstant(arguments, "since")
                    );
                }))
                .build();
    }
}
