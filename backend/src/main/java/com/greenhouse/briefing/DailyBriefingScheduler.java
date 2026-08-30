package com.greenhouse.briefing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

// Produces the day's briefing once it is due, and checks once on startup so a
// platform that was switched off at the scheduled time still produces that
// day's briefing rather than skipping it silently.
//
// There is deliberately ONE schedule setting. This used to carry its own cron
// expression alongside greenhouse.daily-briefing.generate-at, so the two could
// disagree and the cron silently won. Now the scheduler simply ticks every
// minute and asks the service whether a briefing is due; generate-at and zone
// are the only things that decide when that is.
//
// Both paths call generateIfDue, so neither can duplicate a briefing that
// already exists.
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

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT20S")
    public void generateDailyBriefingIfDue() {
        generate(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedBriefingOnStartup() {
        generate(true);
    }

    private void generate(boolean missedRunRecovery) {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping daily briefing check - a previous run is still in progress");
            return;
        }

        try {
            briefingService.generateIfDue(missedRunRecovery).ifPresent(snapshot ->
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
