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
        LocalTime morningAt,
        LocalTime eveningAt,
        Duration window
) {

    public DailyBriefingProperties {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.zone is required, e.g. Europe/London");
        }
        if (morningAt == null) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.morning-at is required, e.g. 06:00");
        }
        if (eveningAt == null) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.evening-at is required, e.g. 19:00");
        }
        // The window and staleness boundaries below assume morning precedes
        // evening within the same day; inverted times would silently produce
        // negative windows rather than failing.
        if (!morningAt.isBefore(eveningAt)) {
            throw new IllegalArgumentException(
                    "greenhouse.daily-briefing.morning-at must be earlier in the day than evening-at");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("greenhouse.daily-briefing.window must be positive");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public LocalTime timeFor(BriefingEdition edition) {
        return edition == BriefingEdition.EVENING ? eveningAt : morningAt;
    }

    public java.time.ZonedDateTime scheduledFor(BriefingEdition edition, java.time.LocalDate day) {
        return day.atTime(timeFor(edition)).atZone(zoneId());
    }

    // Each edition reports on what changed since the PREVIOUS edition, which is
    // what lets the evening one answer "what happened while I was out" rather
    // than repeating the morning (ADR-033).
    public java.time.ZonedDateTime windowStartFor(BriefingEdition edition, java.time.LocalDate day) {
        return edition == BriefingEdition.MORNING
                ? scheduledFor(BriefingEdition.EVENING, day.minusDays(1))
                : scheduledFor(BriefingEdition.MORNING, day);
    }

    // Once the NEXT edition is due, an un-generated one is stale rather than
    // late. Without this, starting the application in the evening would recover
    // a twelve-hour-old morning briefing and send two at once.
    public java.time.ZonedDateTime staleAfter(BriefingEdition edition, java.time.LocalDate day) {
        return edition == BriefingEdition.MORNING
                ? scheduledFor(BriefingEdition.EVENING, day)
                : scheduledFor(BriefingEdition.MORNING, day.plusDays(1));
    }
}
