package com.greenhouse.careloop.outcome;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "greenhouse.outcome-evaluation")
public record OutcomeEvaluationProperties(
        boolean enabled,
        Duration interval,
        Duration initialDelay
) {

    public OutcomeEvaluationProperties {
        // Duration is not supported by @Positive, same as EvaluationProperties.
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("greenhouse.outcome-evaluation.interval must be positive");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "greenhouse.outcome-evaluation.initial-delay must not be negative");
        }
    }
}
