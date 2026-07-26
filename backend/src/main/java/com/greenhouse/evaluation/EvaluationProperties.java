package com.greenhouse.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "greenhouse.evaluation")
public record EvaluationProperties(
        boolean enabled,
        Duration interval,
        Duration initialDelay
) {

    public EvaluationProperties {
        // @Positive doesn't validate java.time.Duration correctly, so these are enforced here instead.
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("greenhouse.evaluation.interval must be positive");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("greenhouse.evaluation.initial-delay must not be negative");
        }
    }
}
