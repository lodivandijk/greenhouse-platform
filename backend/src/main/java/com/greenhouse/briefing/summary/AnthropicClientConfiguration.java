package com.greenhouse.briefing.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Only created when the language-model summary is switched on, so a deployment
// with no API key starts normally and simply gets the deterministic briefing.
@Configuration
@ConditionalOnProperty(
        prefix = "greenhouse.daily-briefing.summary",
        name = "llm-enabled",
        havingValue = "true"
)
public class AnthropicClientConfiguration {

    @Bean
    public AnthropicClient anthropicClient(BriefingSummaryProperties properties) {
        // Reads ANTHROPIC_API_KEY from the environment, the same
        // root-readable env file that holds every other secret on the Pi.
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(properties.timeout())
                .maxRetries(2)
                .build();
    }
}
