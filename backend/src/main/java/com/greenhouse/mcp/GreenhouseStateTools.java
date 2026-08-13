package com.greenhouse.mcp;

import com.greenhouse.state.GreenhouseStateService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GreenhouseStateTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreenhouseStateTools.class);

    private final GreenhouseStateService greenhouseStateService;
    private final McpJsonMapper mcpJsonMapper;

    public GreenhouseStateTools(GreenhouseStateService greenhouseStateService, McpJsonMapper mcpJsonMapper) {
        this.greenhouseStateService = greenhouseStateService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getGreenhouseStateTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_greenhouse_state")
                .description("Returns the current greenhouse state: environmental readings (temperature, "
                        + "humidity, pressure), device connectivity, data freshness, and any active assessments "
                        + "requiring attention. This is the trusted, authoritative current state - do not infer "
                        + "greenhouse conditions from any other source.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{}}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) ->
                        McpToolSupport.execute(LOGGER, mcpJsonMapper, greenhouseStateService::getCurrentState))
                .build();
    }
}
