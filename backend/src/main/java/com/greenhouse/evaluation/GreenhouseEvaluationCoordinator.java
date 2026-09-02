package com.greenhouse.evaluation;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentService;
import com.greenhouse.careloop.CareLoopCorrelationService;
import com.greenhouse.twin.TwinService;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class GreenhouseEvaluationCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreenhouseEvaluationCoordinator.class);

    private final TwinService twinService;
    private final AssessmentService assessmentService;
    private final CareLoopCorrelationService careLoopCorrelationService;
    private final Clock clock;

    public GreenhouseEvaluationCoordinator(
            TwinService twinService,
            AssessmentService assessmentService,
            CareLoopCorrelationService careLoopCorrelationService,
            Clock clock
    ) {
        this.twinService = twinService;
        this.assessmentService = assessmentService;
        this.careLoopCorrelationService = careLoopCorrelationService;
        this.clock = clock;
    }

    public EvaluationResult evaluate() {
        Instant evaluatedAt = clock.instant();
        GreenhouseTwin twin = twinService.getCurrentTwin();

        LOGGER.debug("Starting greenhouse evaluation greenhouseId={} evaluatedAt={}", twin.greenhouseId(), evaluatedAt);

        long startNanos = System.nanoTime();
        AssessmentChanges changes = assessmentService.assessAndReconcile(twin, evaluatedAt);

        // Same tick and same evaluatedAt as the reconciliation that produced
        // these changes, but NOT the same transaction: reconcile() has already
        // committed by the time this runs, and correlate() opens its own.
        //
        // That is accepted eventual consistency rather than atomicity. If
        // correlation fails here, the assessments still stand and the next
        // tick re-correlates from loop state, so an immediately-actionable
        // condition can be up to a minute late but is not lost. Wrapping both
        // in one transaction would mean a bookkeeping failure discarding a
        // legitimate observation about the greenhouse, which is the worse
        // trade. A second scheduler would still race against this one.
        careLoopCorrelationService.correlate(changes, evaluatedAt);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        LOGGER.info(
                "Completed greenhouse evaluation greenhouseId={} raised={} updated={} resolved={} durationMs={}",
                twin.greenhouseId(), changes.raised().size(), changes.updated().size(), changes.resolved().size(), durationMs
        );

        return new EvaluationResult(evaluatedAt, twin.greenhouseId(), changes);
    }
}
