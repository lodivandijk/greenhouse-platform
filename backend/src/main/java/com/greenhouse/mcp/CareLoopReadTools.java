package com.greenhouse.mcp;

import com.greenhouse.careloop.CareLoopQueryService;
import com.greenhouse.careloop.CareLoopSubjectType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CareLoopReadTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(CareLoopReadTools.class);

    private final CareLoopQueryService careLoopQueryService;
    private final McpJsonMapper mcpJsonMapper;

    public CareLoopReadTools(CareLoopQueryService careLoopQueryService, McpJsonMapper mcpJsonMapper) {
        this.careLoopQueryService = careLoopQueryService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getOpenCareLoopsTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_open_care_loops")
                .description("Lists every open care loop - a condition that has been detected and is working "
                        + "its way toward being dealt with. Each entry says what state the loop is in and what "
                        + "the human needs to do next (approve a decision, acknowledge a command, carry out "
                        + "work, or nothing). Start here to find out what needs attention. Optionally filter "
                        + "by subjectType (CROP, GREENHOUSE or DEVICE) and subjectId - for a crop the subjectId "
                        + "is the numeric crop id as a string.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"subjectType\":{\"type\":\"string\",\"description\":\"Optional filter: CROP, GREENHOUSE or DEVICE.\"},"
                        + "\"subjectId\":{\"type\":\"string\",\"description\":\"Optional filter, e.g. '8' for crop 8, or 'greenhouse-01'.\"}"
                        + "},\"required\":[]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        careLoopQueryService.openLoops(
                                McpToolSupport.optionalEnum(request.arguments(), "subjectType", CareLoopSubjectType.class),
                                McpToolSupport.optionalString(request.arguments(), "subjectId")
                        )))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getCareLoopTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_care_loop")
                .description("Returns one care loop's complete history: the assessments that opened it, every "
                        + "decision proposed (including superseded ones) with its approval state, every command "
                        + "issued and how the human responded, what was actually done, and how it turned out. "
                        + "Also returns the scope history - whether each record is still considered relevant to "
                        + "this loop - and the effective decision id, which is the one currently governing the "
                        + "loop. Use this to pick up an in-progress loop with no prior context, or to explain "
                        + "to the user why something was recommended.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"careLoopId\":{\"type\":\"integer\",\"description\":\"The numeric id of the care loop, from get_open_care_loops.\"}"
                        + "},\"required\":[\"careLoopId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () ->
                        careLoopQueryService.loopDetail(
                                McpToolSupport.requireLong(request.arguments(), "careLoopId"))))
                .build();
    }
}
