package com.greenhouse.careloop.outcome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Separate from GreenhouseEvaluationScheduler on purpose: evaluation windows
// are hours or days long and are per-execution, so they do not belong on the
// one-minute twin/assessment tick.
//
// Same shape as that scheduler otherwise - property-gated, reentrancy-guarded,
// and catching its own exceptions so one bad evaluation cannot kill all future
// scheduled runs.
@Component
@ConditionalOnProperty(
        prefix = "greenhouse.outcome-evaluation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutcomeEvaluationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutcomeEvaluationScheduler.class);

    private final OutcomeService outcomeService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutcomeEvaluationScheduler(OutcomeService outcomeService) {
        this.outcomeService = outcomeService;
    }

    @Scheduled(
            fixedDelayString = "${greenhouse.outcome-evaluation.interval:PT5M}",
            initialDelayString = "${greenhouse.outcome-evaluation.initial-delay:PT30S}"
    )
    public void evaluateDueOutcomes() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping outcome evaluation - previous run is still in progress");
            return;
        }

        try {
            List<Outcome> outcomes = outcomeService.evaluateDueOutcomes();
            if (!outcomes.isEmpty()) {
                LOGGER.info("Outcome evaluation completed: {} outcome(s) created", outcomes.size());
            }
        } catch (Exception e) {
            LOGGER.error("Outcome evaluation failed", e);
        } finally {
            running.set(false);
        }
    }
}
