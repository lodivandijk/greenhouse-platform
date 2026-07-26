package com.greenhouse.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "greenhouse.evaluation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GreenhouseEvaluationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreenhouseEvaluationScheduler.class);

    private final GreenhouseEvaluationCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GreenhouseEvaluationScheduler(GreenhouseEvaluationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(
            fixedDelayString = "${greenhouse.evaluation.interval:PT1M}",
            initialDelayString = "${greenhouse.evaluation.initial-delay:PT10S}"
    )
    public void reconcile() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping greenhouse evaluation - previous evaluation is still running");
            return;
        }

        try {
            coordinator.evaluate();
        } catch (Exception e) {
            LOGGER.error("Greenhouse evaluation failed", e);
        } finally {
            running.set(false);
        }
    }
}
