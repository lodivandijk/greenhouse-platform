package com.greenhouse.mcp;

import com.greenhouse.briefing.DailyBriefingService;
import com.greenhouse.briefing.DailyBriefingSnapshot;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
public class DailyBriefingTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyBriefingTools.class);

    private final DailyBriefingService briefingService;
    private final McpJsonMapper mcpJsonMapper;

    public DailyBriefingTools(DailyBriefingService briefingService, McpJsonMapper mcpJsonMapper) {
        this.briefingService = briefingService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getDailyCropStatusTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_daily_crop_status")
                .description("Returns the structured daily status for every active crop: measured greenhouse "
                        + "conditions, each crop's soil state (or an explicit reason it is unknown), active "
                        + "assessments, open care loops with the next action required, recent outcomes, and an "
                        + "explicit list of data-quality gaps. This is evidence, not prose - present it in your "
                        + "own words, leading with anything needing attention, then decisions awaiting "
                        + "approval, then commands awaiting the user, then outcomes since last time, then a "
                        + "short line per remaining crop, then anything unmeasurable. Distinguish measured "
                        + "facts from assessments from your own horticultural advice. Note that moisture index "
                        + "is a 0-100 position between each probe's own dry and wet references, NOT a "
                        + "volumetric water percentage - do not describe it as 'percent water'. Omit "
                        + "greenhouseDay for the latest briefing, or pass it (YYYY-MM-DD) for a specific day.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"greenhouseDay\":{\"type\":\"string\",\"description\":\"Optional YYYY-MM-DD. Defaults to the most recent briefing.\"}"
                        + "},\"required\":[]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    String day = McpToolSupport.optionalString(request.arguments(), "greenhouseDay");

                    Optional<DailyBriefingSnapshot> snapshot = day == null
                            ? briefingService.latestSnapshot()
                            : briefingService.snapshotForDay(LocalDate.parse(day));

                    if (snapshot.isPresent()) {
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("source", "PERSISTED_SNAPSHOT");
                        response.put("snapshotId", snapshot.get().getId());
                        response.put("greenhouseDay", String.valueOf(snapshot.get().getGreenhouseDay()));
                        response.put("generatedAt", String.valueOf(snapshot.get().getGeneratedAt()));
                        response.put("briefing", snapshot.get().getSnapshot());
                        return response;
                    }

                    // No snapshot yet (a fresh install, or a day never
                    // generated) - compute the same structure live rather than
                    // returning nothing useful.
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("source", "COMPUTED_ON_DEMAND");
                    response.put("note", "No persisted briefing exists for that day; this was computed now.");
                    response.put("briefing", briefingService.buildCurrentBriefing());
                    return response;
                }))
                .build();
    }
}
