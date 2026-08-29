package com.greenhouse.careloop.command.catalogue;

import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;
import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The allow-list. A decision may only propose one of these action types, and
// its parameters are validated against the definition here - there is no
// free-text command path into the system (ADR-021).
@Component
public class CommandCatalogue {

    private final Map<CommandType, CommandDefinition> definitions = new LinkedHashMap<>();

    public CommandCatalogue() {
        definitions.put(CommandType.INSPECT_CROP, new CommandDefinition(
                CommandType.INSPECT_CROP,
                Set.of("cropId"),
                Set.of("focus", "notes"),
                false,
                Duration.ofDays(2),
                // Nothing a sensor can confirm; a human records what they saw.
                OutcomeEvaluationMethod.HUMAN_CONFIRMED,
                Duration.ZERO,
                Duration.ofDays(2),
                "Look at the crop and record what you observe."
        ));

        definitions.put(CommandType.WATER_CROP, new CommandDefinition(
                CommandType.WATER_CROP,
                Set.of("cropId", "quantity", "unit"),
                Set.of("notes"),
                true,
                Duration.ofDays(1),
                // A calibrated probe can show the soil responding; without one
                // this falls back to human confirmation at evaluation time.
                OutcomeEvaluationMethod.SENSOR_BASED,
                Duration.ofHours(2),
                Duration.ofHours(12),
                "Water the crop with the stated quantity."
        ));

        definitions.put(CommandType.VENTILATE_GREENHOUSE, new CommandDefinition(
                CommandType.VENTILATE_GREENHOUSE,
                Set.of(),
                Set.of("durationMinutes", "notes"),
                false,
                Duration.ofHours(6),
                // Success is simply the temperature assessment resolving.
                OutcomeEvaluationMethod.ASSESSMENT_RESOLVED,
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                "Open vents or a window to bring the temperature back into range."
        ));

        definitions.put(CommandType.MOVE_OR_SHADE_CROP, new CommandDefinition(
                CommandType.MOVE_OR_SHADE_CROP,
                Set.of("cropId"),
                Set.of("destination", "notes"),
                false,
                Duration.ofDays(1),
                OutcomeEvaluationMethod.HYBRID,
                Duration.ofHours(2),
                Duration.ofHours(12),
                "Move the crop or shade it from direct sun."
        ));

        definitions.put(CommandType.PRUNE_CROP, new CommandDefinition(
                CommandType.PRUNE_CROP,
                Set.of("cropId"),
                Set.of("technique", "notes"),
                false,
                Duration.ofDays(3),
                // The real effect shows up over weeks, so an inconclusive
                // outcome here is normal rather than a failure.
                OutcomeEvaluationMethod.HUMAN_CONFIRMED,
                Duration.ZERO,
                Duration.ofDays(3),
                "Pinch or prune the crop as described."
        ));

        definitions.put(CommandType.FEED_CROP, new CommandDefinition(
                CommandType.FEED_CROP,
                Set.of("cropId"),
                Set.of("feedType", "quantity", "unit", "notes"),
                true,
                Duration.ofDays(2),
                OutcomeEvaluationMethod.HUMAN_CONFIRMED,
                Duration.ofDays(3),
                Duration.ofDays(7),
                "Feed the crop as described."
        ));
    }

    public CommandDefinition definitionFor(CommandType type) {
        CommandDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new DomainValidationException("Unsupported command type: " + type);
        }
        return definition;
    }

    public List<CommandDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public void validateParameters(CommandType type, Map<String, Object> parameters) {
        CommandDefinition definition = definitionFor(type);
        Map<String, Object> supplied = parameters == null ? Map.of() : parameters;

        for (String required : definition.requiredParameters()) {
            Object value = supplied.get(required);
            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new DomainValidationException(
                        "Command " + type + " requires parameter '" + required + "'.");
            }
        }

        for (String key : supplied.keySet()) {
            if (!definition.requiredParameters().contains(key)
                    && !definition.optionalParameters().contains(key)) {
                throw new DomainValidationException(
                        "Command " + type + " does not accept parameter '" + key + "'. Accepted: "
                                + definition.requiredParameters() + " (required), "
                                + definition.optionalParameters() + " (optional).");
            }
        }

        if (definition.quantityApplies() && supplied.get("quantity") != null) {
            Object quantity = supplied.get("quantity");
            if (!(quantity instanceof Number number) || number.doubleValue() <= 0) {
                throw new DomainValidationException("quantity must be a positive number.");
            }
            Object unit = supplied.get("unit");
            if (unit == null || (unit instanceof String s && s.isBlank())) {
                // Same rule the harvest and action domains already enforce:
                // never store an ambiguous bare number.
                throw new DomainValidationException("unit is required when quantity is provided.");
            }
        }
    }
}
