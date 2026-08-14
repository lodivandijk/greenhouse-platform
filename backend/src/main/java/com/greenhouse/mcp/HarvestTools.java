package com.greenhouse.mcp;

import com.greenhouse.crop.HarvestService;
import com.greenhouse.crop.HarvestUnit;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HarvestTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestTools.class);

    private final HarvestService harvestService;
    private final McpJsonMapper mcpJsonMapper;

    public HarvestTools(HarvestService harvestService, McpJsonMapper mcpJsonMapper) {
        this.harvestService = harvestService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordHarvestTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_harvest")
                .description("Records a real harvest event for a crop. Use this when the user describes actually "
                        + "harvesting something, e.g. 'I harvested 180g today.' unit must be one of GRAMS, "
                        + "KILOGRAMS, COUNT - always pick the unit that matches what the user said rather than "
                        + "converting it yourself.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop harvested.\"},"
                        + "\"quantity\":{\"type\":\"number\",\"description\":\"A positive number.\"},"
                        + "\"unit\":{\"type\":\"string\",\"description\":\"One of GRAMS, KILOGRAMS, COUNT.\"},"
                        + "\"harvestedAt\":{\"type\":\"string\",\"description\":\"ISO-8601 timestamp. Defaults to now if omitted.\"},"
                        + "\"notes\":{\"type\":\"string\"}"
                        + "},\"required\":[\"cropId\",\"quantity\",\"unit\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    return harvestService.recordHarvest(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.optionalInstant(arguments, "harvestedAt"),
                            McpToolSupport.optionalDouble(arguments, "quantity"),
                            McpToolSupport.requireEnum(arguments, "unit", HarvestUnit.class),
                            McpToolSupport.optionalString(arguments, "notes")
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification deleteHarvestTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("delete_harvest")
                .description("Permanently deletes a single harvest record - use this to correct a harvest that "
                        + "was recorded in error (wrong quantity, wrong crop, duplicate entry).")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"harvestId\":{\"type\":\"integer\",\"description\":\"The numeric id of the harvest to delete.\"}"
                        + "},\"required\":[\"harvestId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        harvestService.deleteHarvest(McpToolSupport.requireLong(request.arguments(), "harvestId"))))
                .build();
    }
}
