package com.greenhouse.careloop.command.catalogue;

import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;

import java.time.Duration;
import java.util.Set;

// What one allow-listed command type requires and how its result should be
// judged. Held in code rather than configuration because these are contract
// definitions the validation logic depends on, not operator-tunable settings.
public record CommandDefinition(
        CommandType type,
        Set<String> requiredParameters,
        Set<String> optionalParameters,
        boolean quantityApplies,
        Duration defaultExpiry,
        OutcomeEvaluationMethod defaultEvaluationMethod,
        Duration defaultEvaluationDelay,
        Duration defaultEvaluationWindow,
        String humanDescription
) {
}
