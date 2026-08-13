package com.greenhouse.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
public class McpServerConfiguration {

    // Wraps Spring Boot 4's auto-configured Jackson 3 JsonMapper (JSR-310/Instant
    // support already registered) rather than McpJsonDefaults.getMapper()'s bare
    // default, which cannot serialize the java.time types used throughout the domain
    // (GreenhouseTwin, AssessmentResponse, etc). Spring Boot 4 auto-configures Jackson 3
    // (tools.jackson.*), not the classic Jackson 2 com.fasterxml.jackson.* ObjectMapper.
    @Bean
    public McpJsonMapper mcpJsonMapper(JsonMapper jsonMapper) {
        return new JacksonMcpJsonMapper(jsonMapper);
    }

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper mcpJsonMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider mcpTransportProvider) {
        return mcpTransportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            WebMvcStreamableServerTransportProvider mcpTransportProvider,
            List<McpServerFeatures.SyncToolSpecification> toolSpecifications
    ) {
        return McpServer.sync(mcpTransportProvider)
                .serverInfo("greenhouse-platform", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(toolSpecifications)
                .build();
    }
}
