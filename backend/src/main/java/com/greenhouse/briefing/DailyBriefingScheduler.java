package com.greenhouse.briefing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

// Generates the day's briefing at the configured local time, and also checks
// once on startup so a platform that was switched off at 06:00 still produces
// that day's briefing rather than silently skipping it.
//
// Both paths call generateIfMissing, so the recovery cannot duplicate a
// briefing that already exists.
@Component
@ConditionalOnProperty(
        prefix = "greenhouse.daily-briefing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DailyBriefingScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyBriefingScheduler.class);

    private final DailyBriefingService briefingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DailyBriefingScheduler(DailyBriefingService briefingService) {
        this.briefingService = briefingService;
    }

    // Cron rather than fixed-delay: this must land at a wall-clock time in the
    // greenhouse's own timezone, not N hours after the last run.
    @Scheduled(
            cron = "${greenhouse.daily-briefing.cron:0 0 6 * * *}",
            zone = "${greenhouse.daily-briefing.zone:Europe/London}"
    )
    public void generateDailyBriefing() {
        generate(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedBriefingOnStartup() {
        generate(true);
    }

    private void generate(boolean missedRunRecovery) {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping daily briefing generation - a previous run is still in progress");
            return;
        }

        try {
            briefingService.generateIfMissing(missedRunRecovery).ifPresent(snapshot ->
                    LOGGER.info(
                            "Daily briefing snapshot created: id={} day={} recovery={}",
                            snapshot.getId(), snapshot.getGreenhouseDay(), missedRunRecovery
                    ));
        } catch (Exception e) {
            LOGGER.error("Daily briefing generation failed", e);
        } finally {
            running.set(false);
        }
    }
}
