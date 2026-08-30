package com.greenhouse.mcp;

import com.greenhouse.notification.NotificationHistoryService;
import com.greenhouse.notification.NotificationIntentType;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationReadTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationReadTools.class);

    private final NotificationHistoryService historyService;
    private final McpJsonMapper mcpJsonMapper;

    public NotificationReadTools(NotificationHistoryService historyService, McpJsonMapper mcpJsonMapper) {
        this.historyService = historyService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getNotificationHistoryTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_notification_history")
                .description("Returns what the platform decided to notify about and what happened when it "
                        + "tried to deliver it. Each entry is one notification intent with its full ordered "
                        + "delivery history - ATTEMPTED, then SENT, FAILED, SUPPRESSED or ABANDONED. Useful "
                        + "for answering 'did I get told about this?', 'why did I get two emails?', or "
                        + "'is email actually working?'. SUPPRESSED means the situation resolved itself "
                        + "before the message went out, which is normal and not a fault. This is read-only: "
                        + "notifications cannot be created, resent or cancelled through MCP.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"careLoopId\":{\"type\":\"integer\",\"description\":\"Optional: only notifications about this care loop.\"},"
                        + "\"intentType\":{\"type\":\"string\",\"description\":\"Optional: DAILY_BRIEFING, ACTION_REQUIRED, REMINDER or RECOVERY.\"},"
                        + "\"channel\":{\"type\":\"string\",\"description\":\"Optional delivery channel, e.g. EMAIL.\"},"
                        + "\"since\":{\"type\":\"string\",\"description\":\"Optional ISO-8601 timestamp; only notifications created after it.\"}"
                        + "},\"required\":[]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    return historyService.history(
                            arguments.get("careLoopId") instanceof Number n ? n.longValue() : null,
                            McpToolSupport.optionalEnum(arguments, "intentType", NotificationIntentType.class),
                            McpToolSupport.optionalString(arguments, "channel"),
                            McpToolSupport.optionalInstant(arguments, "since")
                    );
                }))
                .build();
    }
}
