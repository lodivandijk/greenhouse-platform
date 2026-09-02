package com.greenhouse.mcp;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.common.IdempotencyConflictException;
import com.greenhouse.common.IdempotencyInProgressException;
import com.greenhouse.common.IdempotencyService;
import com.greenhouse.crop.CropMonitoringProfile;
import com.greenhouse.crop.CropMonitoringProfileService;
import com.greenhouse.crop.SoilMonitoringMode;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Changing how a crop's soil is monitored is configuration, not evidence. It
// gets a real tool with a required rationale rather than being inferred from a
// free-text observation, because a deterministic rule must never depend on
// prose that nobody validated and that has no version (ADR-024).
@Configuration
public class CropMonitoringProfileTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(CropMonitoringProfileTools.class);

    private final CropMonitoringProfileService profileService;
    private final IdempotencyService idempotencyService;
    private final McpJsonMapper mcpJsonMapper;

    public CropMonitoringProfileTools(
            CropMonitoringProfileService profileService,
            IdempotencyService idempotencyService,
            McpJsonMapper mcpJsonMapper
    ) {
        this.profileService = profileService;
        this.idempotencyService = idempotencyService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification setCropSoilMonitoringModeTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("set_crop_soil_monitoring_mode")
                .description("Sets whether a crop's soil is measured by a probe (SENSOR) or judged by a "
                        + "human looking at it (MANUAL). Use MANUAL when a crop deliberately has no probe: "
                        + "the platform then stops reporting the missing sensor as a fault, and any open "
                        + "care loop about it resolves on the next evaluation. "
                        + "MANUAL does NOT mean the soil is fine - it means the platform is not measuring "
                        + "it and will not warn about it, so nothing will tell the user if that crop dries "
                        + "out. Do not set MANUAL to quieten an alert on a crop that actually has a probe; "
                        + "that would remove a real safety net. "
                        + "Setting SENSOR again restores normal sensor assessment immediately, which is "
                        + "what you want once a probe is wired. "
                        + "This creates a new monitoring-profile version and preserves the previous one; "
                        + "every other setting (temperature range, thresholds, strategy) is carried "
                        + "forward unchanged. Setting the mode it already has changes nothing. "
                        + "IMPORTANT: only call this after the user has explicitly asked for it in the "
                        + "current conversation - it changes what the system will and will not warn them "
                        + "about. "
                        + "idempotencyKey: a unique string you generate for this specific request (a UUID "
                        + "is ideal). If the same call is retried with the same key, the original result "
                        + "is returned rather than the change happening twice.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The crop whose monitoring mode is changing.\"},"
                        + "\"mode\":{\"type\":\"string\",\"description\":\"SENSOR or MANUAL.\"},"
                        + "\"rationale\":{\"type\":\"string\",\"description\":\"Why, in the user's terms. Required - the reason must be recorded, not inferred later.\"},"
                        + "\"actorId\":{\"type\":\"string\",\"description\":\"Optional: who asked for this.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"cropId\",\"mode\",\"rationale\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long cropId = McpToolSupport.requireLong(arguments, "cropId");
                    SoilMonitoringMode mode =
                            McpToolSupport.requireEnum(arguments, "mode", SoilMonitoringMode.class);
                    String rationale = McpToolSupport.optionalString(arguments, "rationale");
                    String actorId = McpToolSupport.optionalString(arguments, "actorId");

                    String key = McpToolSupport.optionalString(arguments, "idempotencyKey");

                    Optional<Object> replayed = McpIdempotency.guard(
                            idempotencyService, key, "set_crop_soil_monitoring_mode", arguments);
                    if (replayed.isPresent()) {
                        return replayed.get();
                    }

                    CropMonitoringProfile profile =
                            profileService.changeSoilMonitoringMode(cropId, mode, rationale, actorId);
                    Object view = describe(profile);

                    try {
                        idempotencyService.complete(key, mcpJsonMapper.writeValueAsString(view));
                    } catch (Exception e) {
                        // The change itself succeeded; failing to cache the
                        // response for replay must not fail the call.
                        LOGGER.warn("Could not store idempotent result for key {}: {}", key, e.getMessage());
                    }
                    return view;
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getCropMonitoringProfileHistoryTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_crop_monitoring_profile_history")
                .description("Returns every version of a crop's monitoring profile, newest first, including "
                        + "superseded ones. Use this to answer why a crop is or is not being sensor-assessed, "
                        + "when that changed, and who changed it. Exactly one version is enabled at a time; "
                        + "the rest are history and are never edited.")
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"cropId\":{\"type\":\"integer\",\"description\":\"The crop to report on.\"}"
                        + "},\"required\":[\"cropId\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    Long cropId = McpToolSupport.requireLong(request.arguments(), "cropId");
                    List<Map<String, Object>> versions = profileService.listVersionHistory(cropId).stream()
                            .map(this::describe)
                            .toList();
                    return Map.of("cropId", cropId, "versions", versions);
                }))
                .build();
    }

    private Map<String, Object> describe(CropMonitoringProfile profile) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("profileId", profile.getId());
        described.put("cropId", profile.getCropId());
        described.put("version", profile.getVersion());
        described.put("enabled", profile.getEnabled());
        described.put("soilMonitoringMode", String.valueOf(profile.getSoilMonitoringMode()));
        described.put("soilMoistureStrategy", String.valueOf(profile.getSoilMoistureStrategy()));
        described.put("soilDryThresholdIndex", profile.getSoilDryThresholdIndex());
        described.put("soilWetThresholdIndex", profile.getSoilWetThresholdIndex());
        described.put("preferredTemperatureMinCelsius", profile.getPreferredTemperatureMinCelsius());
        described.put("preferredTemperatureMaxCelsius", profile.getPreferredTemperatureMaxCelsius());
        described.put("createdAt", String.valueOf(profile.getCreatedAt()));
        described.put("createdBy", profile.getCreatedBy());
        described.put("sourceNotes", profile.getSourceNotes());
        described.put("supersedesProfileId", profile.getSupersedesProfileId());
        if (profile.isManuallyMonitored()) {
            described.put("note", "Soil is not measured for this crop. Its condition is unknown to the "
                    + "platform and no soil warning will ever be raised for it.");
        }
        return described;
    }
}
