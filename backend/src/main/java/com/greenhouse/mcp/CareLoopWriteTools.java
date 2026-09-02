package com.greenhouse.mcp;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.CareLoopQueryService;
import com.greenhouse.careloop.CareLoopService;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEventType;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.ExecutionResult;
import com.greenhouse.careloop.execution.ExecutionService;
import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;
import com.greenhouse.careloop.outcome.OutcomeResult;
import com.greenhouse.careloop.outcome.OutcomeService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.common.DomainValidationException;
import com.greenhouse.common.IdempotencyConflictException;
import com.greenhouse.common.IdempotencyInProgressException;
import com.greenhouse.common.IdempotencyService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

// Every tool here requires an idempotencyKey, and every tool that records a
// human's answer states in its own description that it may only be called
// after the user has actually said so in the current conversation. That
// contract is the thing standing between "the agent proposed something" and
// "the system believes a human approved it" (ADR-021).
@Configuration
public class CareLoopWriteTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(CareLoopWriteTools.class);

    private static final String HUMAN_CONFIRMATION_NOTICE =
            " IMPORTANT: only call this after the user has explicitly said so in the current conversation. "
                    + "Do not call it on your own initiative, on assumption, or to 'move things along'. "
                    + "It is recorded as a human decision relayed by you (HUMAN_VIA_AGENT).";

    private static final String IDEMPOTENCY_NOTICE =
            "idempotencyKey: a unique string you generate for this specific request (a UUID is ideal). "
                    + "If the same call is retried with the same key, the original result is returned rather "
                    + "than the action happening twice.";

    private final CareLoopService careLoopService;
    private final CareLoopQueryService careLoopQueryService;
    private final DecisionService decisionService;
    private final CommandService commandService;
    private final ExecutionService executionService;
    private final OutcomeService outcomeService;
    private final IdempotencyService idempotencyService;
    private final McpJsonMapper mcpJsonMapper;

    public CareLoopWriteTools(
            CareLoopService careLoopService,
            CareLoopQueryService careLoopQueryService,
            DecisionService decisionService,
            CommandService commandService,
            ExecutionService executionService,
            OutcomeService outcomeService,
            IdempotencyService idempotencyService,
            McpJsonMapper mcpJsonMapper
    ) {
        this.careLoopService = careLoopService;
        this.careLoopQueryService = careLoopQueryService;
        this.decisionService = decisionService;
        this.commandService = commandService;
        this.executionService = executionService;
        this.outcomeService = outcomeService;
        this.idempotencyService = idempotencyService;
        this.mcpJsonMapper = mcpJsonMapper;
    }

    // Runs the action behind the idempotency guard and always returns the
    // resulting loop view, so the caller sees the loop's new state without a
    // second round trip.
    private Object idempotent(
            McpSchema.CallToolRequest request,
            String toolName,
            Supplier<Long> action
    ) {
        String key = McpToolSupport.optionalString(request.arguments(), "idempotencyKey");

        Optional<Object> replayed = McpIdempotency.guard(
                idempotencyService, key, toolName, request.arguments());
        if (replayed.isPresent()) {
            return replayed.get();
        }

        Long careLoopId = action.get();
        Object view = careLoopQueryService.loopDetail(careLoopId);

        try {
            idempotencyService.complete(key, mcpJsonMapper.writeValueAsString(view));
        } catch (Exception e) {
            // The work itself succeeded; failing to cache the response for
            // replay must not fail the call. The reservation then stays
            // IN_PROGRESS, so an immediate retry is told to wait rather than
            // duplicating the work, and only after the reservation timeout is
            // the action re-run.
            LOGGER.warn("Could not store idempotent result for key {}: {}", key, e.getMessage());
        }
        return view;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification proposeCareDecisionTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("propose_care_decision")
                .description("Proposes a course of action for an open care loop. This does NOT cause anything "
                        + "to happen - it records a proposal that the user must then approve via "
                        + "record_decision_response before any command is issued. You may call this on your own "
                        + "initiative when a loop has an actionable condition. actionType must be one of "
                        + "INSPECT_CROP, WATER_CROP, VENTILATE_GREENHOUSE, MOVE_OR_SHADE_CROP, PRUNE_CROP or "
                        + "FEED_CROP. parameters depends on the type: WATER_CROP needs cropId, quantity and "
                        + "unit; INSPECT_CROP/PRUNE_CROP/MOVE_OR_SHADE_CROP/FEED_CROP need cropId; "
                        + "VENTILATE_GREENHOUSE needs none. Give a rationale explaining the evidence, and an "
                        + "expectedEffect describing what should change if this works - the outcome is judged "
                        + "against it later. Set supersedesDecisionId when the user asks for a change to an "
                        + "existing proposal; the original is preserved. " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"careLoopId\":{\"type\":\"integer\",\"description\":\"The loop this responds to.\"},"
                        + "\"actionType\":{\"type\":\"string\",\"description\":\"INSPECT_CROP, WATER_CROP, VENTILATE_GREENHOUSE, MOVE_OR_SHADE_CROP, PRUNE_CROP or FEED_CROP.\"},"
                        + "\"parameters\":{\"type\":\"object\",\"description\":\"Action parameters, e.g. {\\\"cropId\\\":8,\\\"quantity\\\":200,\\\"unit\\\":\\\"ml\\\"}.\"},"
                        + "\"rationale\":{\"type\":\"string\",\"description\":\"Why this action, citing the evidence.\"},"
                        + "\"expectedEffect\":{\"type\":\"string\",\"description\":\"What should change if this works.\"},"
                        + "\"successCriteria\":{\"type\":\"string\",\"description\":\"Optional, how success would be recognised.\"},"
                        + "\"assessmentIds\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"Optional assessment ids this responds to.\"},"
                        + "\"evaluationMethod\":{\"type\":\"string\",\"description\":\"Optional: SENSOR_BASED, HUMAN_CONFIRMED, HYBRID or ASSESSMENT_RESOLVED. Sensible default per action type.\"},"
                        + "\"supersedesDecisionId\":{\"type\":\"integer\",\"description\":\"Optional: the decision this replaces.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"careLoopId\",\"actionType\",\"rationale\",\"expectedEffect\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long careLoopId = McpToolSupport.requireLong(arguments, "careLoopId");
                    return idempotent(request, "propose_care_decision", () -> {
                        decisionService.propose(
                                careLoopId,
                                McpToolSupport.requireEnum(arguments, "actionType", CommandType.class),
                                asStringKeyedMap(arguments.get("parameters")),
                                McpToolSupport.optionalString(arguments, "rationale"),
                                asLongList(arguments.get("assessmentIds")),
                                asLongList(arguments.get("goalIds")),
                                McpToolSupport.optionalString(arguments, "expectedEffect"),
                                McpToolSupport.optionalEnum(arguments, "evaluationMethod", OutcomeEvaluationMethod.class),
                                null,
                                null,
                                McpToolSupport.optionalString(arguments, "successCriteria"),
                                // Proposing is genuinely the agent's own act.
                                ActorType.AGENT,
                                null,
                                asLong(arguments.get("supersedesDecisionId")),
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return careLoopId;
                    });
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordDecisionResponseTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_decision_response")
                .description("Records that the user approved or rejected a proposed decision. Approving issues "
                        + "the corresponding command to the human; rejecting issues nothing. A decision can "
                        + "only be answered once, and a superseded decision cannot be answered at all - respond "
                        + "to its replacement instead."
                        + HUMAN_CONFIRMATION_NOTICE + " " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"decisionId\":{\"type\":\"integer\",\"description\":\"The decision the user is answering.\"},"
                        + "\"response\":{\"type\":\"string\",\"description\":\"APPROVED or REJECTED.\"},"
                        + "\"reasonText\":{\"type\":\"string\",\"description\":\"Optional: the user's stated reason.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"decisionId\",\"response\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long decisionId = McpToolSupport.requireLong(arguments, "decisionId");
                    Decision decision = decisionService.requireDecision(decisionId);
                    return idempotent(request, "record_decision_response", () -> {
                        careLoopService.respondToDecision(
                                decisionId,
                                McpToolSupport.requireEnum(arguments, "response", DecisionLifecycleEventType.class),
                                McpToolSupport.optionalString(arguments, "reasonText"),
                                ActorType.HUMAN_VIA_AGENT,
                                null,
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return decision.getCareLoopId();
                    });
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordCommandResponseTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_command_response")
                .description("Records how the user responded to an issued command: ACKNOWLEDGED (they will do "
                        + "it), DEFERRED (later - optionally give deferredUntil), or DECLINED (they will not). "
                        + "Acknowledging is not the same as doing it: record the actual work with "
                        + "record_care_execution afterwards."
                        + HUMAN_CONFIRMATION_NOTICE + " " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"commandId\":{\"type\":\"integer\",\"description\":\"The command being answered.\"},"
                        + "\"response\":{\"type\":\"string\",\"description\":\"ACKNOWLEDGED, DEFERRED or DECLINED.\"},"
                        + "\"reasonText\":{\"type\":\"string\",\"description\":\"Optional: the user's stated reason.\"},"
                        + "\"deferredUntil\":{\"type\":\"string\",\"description\":\"Optional ISO-8601 timestamp when DEFERRED.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"commandId\",\"response\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long commandId = McpToolSupport.requireLong(arguments, "commandId");
                    Long careLoopId = commandService.requireCommand(commandId).getCareLoopId();
                    return idempotent(request, "record_command_response", () -> {
                        commandService.recordResponse(
                                commandId,
                                McpToolSupport.requireEnum(arguments, "response", CommandLifecycleEventType.class),
                                McpToolSupport.optionalString(arguments, "reasonText"),
                                McpToolSupport.optionalInstant(arguments, "deferredUntil"),
                                ActorType.HUMAN_VIA_AGENT,
                                null,
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return careLoopId;
                    });
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordCareExecutionTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_care_execution")
                .description("Records what the user ACTUALLY did, which may differ from what the command asked "
                        + "for - that difference matters and is preserved. Put the real figures in "
                        + "actualParameters, e.g. {\"actualQuantity\":200,\"actualUnit\":\"ml\"} even if the "
                        + "command said 300ml. result is COMPLETED, PARTIAL or FAILED. Ask the user for the "
                        + "quantity if they have not said; only omit it if they genuinely do not know."
                        + HUMAN_CONFIRMATION_NOTICE + " Never record an execution you have not been told "
                        + "happened. " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"commandId\":{\"type\":\"integer\",\"description\":\"The command that was carried out.\"},"
                        + "\"result\":{\"type\":\"string\",\"description\":\"COMPLETED, PARTIAL or FAILED.\"},"
                        + "\"actualParameters\":{\"type\":\"object\",\"description\":\"What was actually done, e.g. {\\\"actualQuantity\\\":200,\\\"actualUnit\\\":\\\"ml\\\"}.\"},"
                        + "\"completedAt\":{\"type\":\"string\",\"description\":\"Optional ISO-8601 time it was done. Defaults to now.\"},"
                        + "\"startedAt\":{\"type\":\"string\",\"description\":\"Optional ISO-8601 start time.\"},"
                        + "\"notes\":{\"type\":\"string\",\"description\":\"Optional free-text detail from the user.\"},"
                        + "\"correctsExecutionId\":{\"type\":\"integer\",\"description\":\"Optional: corrects an earlier execution rather than editing it.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"commandId\",\"result\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long commandId = McpToolSupport.requireLong(arguments, "commandId");
                    Long careLoopId = commandService.requireCommand(commandId).getCareLoopId();
                    return idempotent(request, "record_care_execution", () -> {
                        executionService.record(
                                commandId,
                                McpToolSupport.requireEnum(arguments, "result", ExecutionResult.class),
                                asStringKeyedMap(arguments.get("actualParameters")),
                                ActorType.HUMAN_VIA_AGENT,
                                null,
                                McpToolSupport.optionalInstant(arguments, "startedAt"),
                                McpToolSupport.optionalInstant(arguments, "completedAt"),
                                McpToolSupport.optionalString(arguments, "notes"),
                                asLong(arguments.get("correctsExecutionId")),
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return careLoopId;
                    });
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordOutcomeReviewTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_outcome_review")
                .description("Records the user's view on an outcome the system evaluated - adding context, "
                        + "disputing it, or correcting it. Supply correctedResult (SUCCESS, PARTIAL, FAILED or "
                        + "INCONCLUSIVE) only if the user is actually saying the result was wrong; that creates "
                        + "a new outcome superseding the original, which is preserved either way."
                        + HUMAN_CONFIRMATION_NOTICE + " " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"outcomeId\":{\"type\":\"integer\",\"description\":\"The outcome being reviewed.\"},"
                        + "\"reviewNote\":{\"type\":\"string\",\"description\":\"What the user said about it.\"},"
                        + "\"disputed\":{\"type\":\"boolean\",\"description\":\"True if the user disagrees with the evaluation.\"},"
                        + "\"correctedResult\":{\"type\":\"string\",\"description\":\"Optional: SUCCESS, PARTIAL, FAILED or INCONCLUSIVE, if the user is correcting it.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"outcomeId\",\"reviewNote\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long outcomeId = McpToolSupport.requireLong(arguments, "outcomeId");
                    Boolean disputed = McpToolSupport.optionalBoolean(arguments, "disputed");
                    return idempotent(request, "record_outcome_review", () -> {
                        var review = outcomeService.recordReview(
                                outcomeId,
                                McpToolSupport.optionalString(arguments, "reviewNote"),
                                disputed != null && disputed,
                                null,
                                McpToolSupport.optionalEnum(arguments, "correctedResult", OutcomeResult.class),
                                ActorType.HUMAN_VIA_AGENT,
                                null,
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return outcomeService.careLoopIdForOutcome(review.getOutcomeId());
                    });
                }))
                .build();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification recordLoopScopeOverrideTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("record_loop_scope_override")
                .description("Marks a record as no longer relevant to a care loop (or relevant again), with a "
                        + "reason. This is exceptional - normal records are scoped automatically and need no "
                        + "management. Use it when evidence turns out to be invalid: a probe found outside its "
                        + "pot, a record attached to the wrong crop, a duplicate. The record itself is never "
                        + "deleted or altered; only its relevance to this loop changes, and the change is "
                        + "itself recorded. Good reasonCodes: INVALID_SENSOR_POSITION, WRONG_CROP, DUPLICATE, "
                        + "STALE_EVIDENCE."
                        + HUMAN_CONFIRMATION_NOTICE + " " + IDEMPOTENCY_NOTICE)
                .inputSchema(mcpJsonMapper, "{\"type\":\"object\",\"properties\":{"
                        + "\"careLoopId\":{\"type\":\"integer\",\"description\":\"The loop whose scope is changing.\"},"
                        + "\"recordType\":{\"type\":\"string\",\"description\":\"ASSESSMENT, DECISION, COMMAND, EXECUTION or OUTCOME.\"},"
                        + "\"recordId\":{\"type\":\"integer\",\"description\":\"The id of that record.\"},"
                        + "\"scope\":{\"type\":\"string\",\"description\":\"IN_SCOPE or OUT_OF_SCOPE.\"},"
                        + "\"reasonCode\":{\"type\":\"string\",\"description\":\"Short machine-readable reason, e.g. INVALID_SENSOR_POSITION.\"},"
                        + "\"reasonText\":{\"type\":\"string\",\"description\":\"What the user actually said.\"},"
                        + "\"idempotencyKey\":{\"type\":\"string\",\"description\":\"Unique key for this request.\"}"
                        + "},\"required\":[\"careLoopId\",\"recordType\",\"recordId\",\"scope\",\"reasonCode\",\"idempotencyKey\"]}")
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, request, () -> {
                    var arguments = request.arguments();
                    Long careLoopId = McpToolSupport.requireLong(arguments, "careLoopId");
                    return idempotent(request, "record_loop_scope_override", () -> {
                        careLoopService.recordScopeOverride(
                                careLoopId,
                                McpToolSupport.requireEnum(arguments, "recordType", LoopRecordType.class),
                                McpToolSupport.requireLong(arguments, "recordId"),
                                McpToolSupport.requireEnum(arguments, "scope", LoopScope.class),
                                McpToolSupport.optionalString(arguments, "reasonCode"),
                                McpToolSupport.optionalString(arguments, "reasonText"),
                                ActorType.HUMAN_VIA_AGENT,
                                null,
                                McpToolSupport.optionalString(arguments, "idempotencyKey")
                        );
                        return careLoopId;
                    });
                }))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new DomainValidationException("Expected an object value but received: " + value);
    }

    private static List<Long> asLongList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Number)
                    .map(item -> ((Number) item).longValue())
                    .toList();
        }
        throw new DomainValidationException("Expected an array of numbers but received: " + value);
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
