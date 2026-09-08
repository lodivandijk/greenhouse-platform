package com.greenhouse.briefing.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// Configuration for the language-model summary.
//
// Disabled by default and keyless: the briefing must be producible by a
// deployment that has never heard of the Anthropic API (ADR-029).
@Validated
@ConfigurationProperties(prefix = "greenhouse.briefing.summary")
public record BriefingSummaryProperties(
        boolean llmEnabled,
        String model,
        int maxTokens,
        Duration timeout
) {

    public BriefingSummaryProperties {
        if (model == null || model.isBlank()) {
            model = "claude-opus-5";
        }
        if (maxTokens <= 0) {
            maxTokens = 2000;
        }
        // A briefing is generated on a scheduler; it must not hang there.
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(60);
        }
    }
}
