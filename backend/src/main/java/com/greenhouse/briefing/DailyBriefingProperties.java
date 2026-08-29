package com.greenhouse.briefing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "greenhouse.daily-briefing")
public record DailyBriefingProperties(
        boolean enabled,
        String zone,
        LocalTime generateAt,
        Duration window
) {

    public DailyBriefingProperties {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.zone is required, e.g. Europe/London");
        }
        if (generateAt == null) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.generate-at is required, e.g. 06:00");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.window must be positive");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
