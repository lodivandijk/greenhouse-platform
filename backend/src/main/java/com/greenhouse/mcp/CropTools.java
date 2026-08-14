package com.greenhouse.mcp;

import com.greenhouse.crop.CropHistoryService;
import com.greenhouse.crop.CropService;
import com.greenhouse.crop.CropStatus;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CropTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(CropTools.class);

    private final CropService cropService;
    private final CropHistoryService cropHistoryService;
    private final McpJsonMapper mcpJsonMapper;

    public CropTools(CropService cropService, CropHistoryService cropHistoryService, McpJsonMapper mcpJsonMapper) {
        this.cropService = cropService;
        this.cropHistoryService = cropHistoryService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification listCropsTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("list_crops")
                .description("Lists every crop currently tracked by the greenhouse, including ended ones. "
                        + "Use this to find a crop's id before calling get_crop, get_crop_history, create_goal, "
                        + "record_harvest or record_crop_observation.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{}}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) ->
                        McpToolSupport.execute(LOGGER, mcpJsonMapper, request, cropService::listCrops))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getCropTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_crop")
                .description("Returns one crop's current details (species, variety, location, status, dates, "
                        + "notes). Does not include its goals, harvests or observation history - use "
                        + "get_crop_history for that.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop, from list_crops.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        cropService.getCrop(McpToolSupport.requireLong(request.arguments(), "cropId"))))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getCropHistoryTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_crop_history")
                .description("Returns a crop's full recorded timeline: its details, all goals ever set for it, "
                        + "its complete harvest history, and every manual crop observation recorded for it. "
                        + "This does not include raw environmental sensor telemetry - use get_greenhouse_state "
                        + "for current environmental conditions.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop, from list_crops.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        cropHistoryService.getCropHistory(McpToolSupport.requireLong(request.arguments(), "cropId"))))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification createCropTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("create_crop")
                .description("Records that a new crop has been planted in the greenhouse. Use this when the user "
                        + "describes a real planting event, not a hypothetical or future plan. The crop starts in "
                        + "ESTABLISHING status.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"species\":{\"type\":\"string\",\"description\":\"What is being grown, e.g. 'Basil'.\"},"
                        + "\"variety\":{\"type\":\"string\",\"description\":\"Optional cultivar/variety, e.g. 'Genovese'.\"},"
                        + "\"location\":{\"type\":\"string\",\"description\":\"Where it is growing, e.g. 'planter-02' or 'zone-main'.\"},"
                        + "\"plantedAt\":{\"type\":\"string\",\"description\":\"ISO-8601 timestamp. Defaults to now if omitted.\"},"
                        + "\"notes\":{\"type\":\"string\",\"description\":\"Optional free-text notes.\"}"
                        + "},\"required\":[\"species\",\"location\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    String species = McpToolSupport.optionalString(arguments, "species");
                    String location = McpToolSupport.optionalString(arguments, "location");
                    return cropService.createCrop(
                            species,
                            McpToolSupport.optionalString(arguments, "variety"),
                            location,
                            McpToolSupport.optionalInstant(arguments, "plantedAt"),
                            McpToolSupport.optionalString(arguments, "notes")
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification updateCropTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("update_crop")
                .description("Updates one or more fields on an existing crop (variety, location, status, notes, "
                        + "or when it ended). Only the fields provided are changed. status must be one of "
                        + "PLANNED, ESTABLISHING, PRODUCTIVE, DECLINING, ENDED.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop to update.\"},"
                        + "\"variety\":{\"type\":\"string\"},"
                        + "\"location\":{\"type\":\"string\"},"
                        + "\"status\":{\"type\":\"string\",\"description\":\"One of PLANNED, ESTABLISHING, PRODUCTIVE, DECLINING, ENDED.\"},"
                        + "\"notes\":{\"type\":\"string\"},"
                        + "\"endedAt\":{\"type\":\"string\",\"description\":\"ISO-8601 timestamp, set when status becomes ENDED.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    return cropService.updateCrop(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.optionalString(arguments, "variety"),
                            McpToolSupport.optionalString(arguments, "location"),
                            McpToolSupport.optionalEnum(arguments, "status", CropStatus.class),
                            McpToolSupport.optionalString(arguments, "notes"),
                            McpToolSupport.optionalInstant(arguments, "endedAt")
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification deleteCropTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("delete_crop")
                .description("Permanently deletes a crop. Only works if the crop has no recorded goals, harvests "
                        + "or observations - this is for correcting a mistake (e.g. a crop created with the wrong "
                        + "species, or a duplicate), not for ending a real crop's life. To retire a crop that has "
                        + "real history, use update_crop with status ENDED instead. If this tool reports the crop "
                        + "has history, ask the user whether they meant to retire it rather than delete it.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The numeric id of the crop to delete.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        cropService.deleteCrop(McpToolSupport.requireLong(request.arguments(), "cropId"))))
                .build();
    }
}
