package com.greenhouse.mcp;

import com.greenhouse.crop.CropObservationMetric;
import com.greenhouse.crop.CropObservationService;
import com.greenhouse.crop.CropObservationSource;
import com.greenhouse.crop.CropObservationValueType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CropObservationTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(CropObservationTools.class);

    private final CropObservationService cropObservationService;
    private final McpJsonMapper mcpJsonMapper;

    public CropObservationTools(CropObservationService cropObservationService, McpJsonMapper mcpJsonMapper) {
        this.cropObservationService = cropObservationService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordCropObservationTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_crop_observation")
                .description("Records a manual, semantic observation about a crop's biological state - health, "
                        + "flowering, woodiness, taste, growth - as distinct from machine sensor telemetry. Set "
                        + "valueType to say which of numericValue, textValue or booleanValue you are providing, "
                        + "and set exactly that one field; leave the other two unset. metric should be the closest "
                        + "match from PLANT_HEALTH, STEM_WOODINESS, FLOWER_COUNT, FLOWERING_STAGE, LEAF_COLOR, "
                        + "GROWTH_RATE, DISEASE_SIGNS, SWEETNESS_SCORE, BRIX, or OTHER if nothing fits (put detail "
                        + "in notes when using OTHER). source should be HUMAN for anything the user directly told "
                        + "you.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\"},"
                        + "\"metric\":{\"type\":\"string\",\"description\":\"One of PLANT_HEALTH, STEM_WOODINESS, FLOWER_COUNT, FLOWERING_STAGE, LEAF_COLOR, GROWTH_RATE, DISEASE_SIGNS, SWEETNESS_SCORE, BRIX, OTHER.\"},"
                        + "\"valueType\":{\"type\":\"string\",\"description\":\"One of NUMERIC, TEXT, BOOLEAN - says which value field below is populated.\"},"
                        + "\"numericValue\":{\"type\":\"number\",\"description\":\"Set only when valueType is NUMERIC.\"},"
                        + "\"textValue\":{\"type\":\"string\",\"description\":\"Set only when valueType is TEXT.\"},"
                        + "\"booleanValue\":{\"type\":\"boolean\",\"description\":\"Set only when valueType is BOOLEAN.\"},"
                        + "\"unit\":{\"type\":\"string\",\"description\":\"Optional free-text unit for numericValue, e.g. 'cm' or 'count'.\"},"
                        + "\"source\":{\"type\":\"string\",\"description\":\"One of HUMAN, AI_DERIVED, DERIVED, EXTERNAL. Use HUMAN for anything the user told you directly.\"},"
                        + "\"confidence\":{\"type\":\"number\",\"description\":\"Optional, 0.0 to 1.0.\"},"
                        + "\"observedAt\":{\"type\":\"string\",\"description\":\"ISO-8601 timestamp. Defaults to now if omitted.\"},"
                        + "\"notes\":{\"type\":\"string\"}"
                        + "},\"required\":[\"cropId\",\"metric\",\"valueType\",\"source\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () -> {
                    var arguments = request.arguments();
                    return cropObservationService.recordObservation(
                            McpToolSupport.requireLong(arguments, "cropId"),
                            McpToolSupport.requireEnum(arguments, "metric", CropObservationMetric.class),
                            McpToolSupport.requireEnum(arguments, "valueType", CropObservationValueType.class),
                            McpToolSupport.optionalDouble(arguments, "numericValue"),
                            McpToolSupport.optionalString(arguments, "textValue"),
                            McpToolSupport.optionalBoolean(arguments, "booleanValue"),
                            McpToolSupport.optionalString(arguments, "unit"),
                            McpToolSupport.requireEnum(arguments, "source", CropObservationSource.class),
                            McpToolSupport.optionalDouble(arguments, "confidence"),
                            McpToolSupport.optionalInstant(arguments, "observedAt"),
                            McpToolSupport.optionalString(arguments, "notes"),
                            null
                    );
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification deleteCropObservationTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("delete_crop_observation")
                .description("Permanently deletes a single crop observation - use this to correct an observation "
                        + "that was recorded in error (wrong metric, wrong value, wrong crop).")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"observationId\":{\"type\":\"integer\",\"description\":\"The numeric id of the observation to delete.\"}"
                        + "},\"required\":[\"observationId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () ->
                        cropObservationService.deleteObservation(
                                McpToolSupport.requireLong(request.arguments(), "observationId"))))
                .build();
    }
}
