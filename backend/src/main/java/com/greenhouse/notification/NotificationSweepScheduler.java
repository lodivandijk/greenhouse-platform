package com.greenhouse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// The notification policy and delivery sweep.
//
// This is NOT a second assessment engine: it reads care-loop and briefing state
// that the one-minute evaluation already produced, and never re-evaluates a
// sensor threshold itself.
//
// Same shape as the other schedulers - property-gated, reentrancy-guarded, and
// swallowing its own exceptions so one bad intent cannot kill future runs.
@Component
@ConditionalOnProperty(
        prefix = "greenhouse.notifications",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationSweepScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSweepScheduler.class);

    private final NotificationPolicyService policyService;
    private final NotificationDeliveryService deliveryService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NotificationSweepScheduler(
            NotificationPolicyService policyService,
            NotificationDeliveryService deliveryService
    ) {
        this.policyService = policyService;
        this.deliveryService = deliveryService;
    }

    @Scheduled(
            fixedDelayString = "${greenhouse.notifications.sweep-interval:PT5M}",
            initialDelayString = "${greenhouse.notifications.initial-delay:PT45S}"
    )
    public void sweep() {
        run();
    }

    // Recovers work missed while the application was down - an intent created
    // before a restart still has its delivery attempted.
    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        run();
    }

    private void run() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping notification sweep - a previous sweep is still running");
            return;
        }

        try {
            List<NotificationIntent> created = policyService.findAndRecordCandidates();
            if (!created.isEmpty()) {
                LOGGER.info("Notification intents created: {}", created.size());
            }
        } catch (Exception e) {
            // Policy failing must not prevent delivery of intents already recorded.
            LOGGER.error("Notification policy sweep failed", e);
        }

        try {
            int delivered = deliveryService.deliverPending();
            if (delivered > 0) {
                LOGGER.info("Notifications delivered: {}", delivered);
            }
        } catch (Exception e) {
            LOGGER.error("Notification delivery sweep failed", e);
        } finally {
            running.set(false);
        }
    }
}
