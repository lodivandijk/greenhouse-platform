package com.greenhouse.careloop.outcome;

import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.Execution;
import com.greenhouse.careloop.execution.ExecutionRepository;
import com.greenhouse.careloop.execution.ExecutionResult;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.common.DomainValidationException;
import com.greenhouse.observation.SoilMoistureReadingEntity;
import com.greenhouse.observation.SoilMoistureReadingRepository;
import com.greenhouse.observation.assignment.CropSensorAssignment;
import com.greenhouse.observation.assignment.CropSensorAssignmentService;
import com.greenhouse.observation.calibration.MoistureIndex;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Judges an execution against its decision's expected effect once the
// evaluation window has elapsed.
//
// The governing rule: when the evidence needed to judge is not available, the
// result is INCONCLUSIVE. Never SUCCESS by default, never a guess (ADR-021).
@Service
public class OutcomeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutcomeService.class);

    private final OutcomeRepository outcomeRepository;
    private final OutcomeReviewEventRepository reviewEventRepository;
    private final OutcomeEvaluationScheduleRepository scheduleRepository;
    private final ExecutionRepository executionRepository;
    private final CommandService commandService;
    private final DecisionService decisionService;
    private final AssessmentRepository assessmentRepository;
    private final SoilMoistureReadingRepository soilMoistureReadingRepository;
    private final CropSensorAssignmentService assignmentService;
    private final SensorCalibrationService calibrationService;
    private final ScopeService scopeService;
    private final Clock clock;

    public OutcomeService(
            OutcomeRepository outcomeRepository,
            OutcomeReviewEventRepository reviewEventRepository,
            OutcomeEvaluationScheduleRepository scheduleRepository,
            ExecutionRepository executionRepository,
            CommandService commandService,
            DecisionService decisionService,
            AssessmentRepository assessmentRepository,
            SoilMoistureReadingRepository soilMoistureReadingRepository,
            CropSensorAssignmentService assignmentService,
            SensorCalibrationService calibrationService,
            ScopeService scopeService,
            Clock clock
    ) {
        this.outcomeRepository = outcomeRepository;
        this.reviewEventRepository = reviewEventRepository;
        this.scheduleRepository = scheduleRepository;
        this.executionRepository = executionRepository;
        this.commandService = commandService;
        this.decisionService = decisionService;
        this.assessmentRepository = assessmentRepository;
        this.soilMoistureReadingRepository = soilMoistureReadingRepository;
        this.assignmentService = assignmentService;
        this.calibrationService = calibrationService;
        this.scopeService = scopeService;
        this.clock = clock;
    }

    // Evaluates every execution whose window has elapsed. Survives restarts
    // because the schedule is a table, not an in-memory timer.
    @Transactional
    public List<Outcome> evaluateDueOutcomes() {
        Instant now = clock.instant();
        List<OutcomeEvaluationSchedule> due =
                scheduleRepository.findAllByCompletedAtIsNullAndEvaluateAfterBefore(now);

        return due.stream()
                .map(schedule -> evaluate(schedule, now))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Outcome> evaluate(OutcomeEvaluationSchedule schedule, Instant now) {
        Optional<Execution> executionOpt = executionRepository.findById(schedule.getExecutionId());
        if (executionOpt.isEmpty()) {
            schedule.setCompletedAt(now);
            scheduleRepository.save(schedule);
            return Optional.empty();
        }

        Execution execution = executionOpt.get();
        Command command = commandService.requireCommand(execution.getCommandId());
        Decision decision = decisionService.requireDecision(command.getDecisionId());

        Evaluation evaluation = switch (decision.getEvaluationMethod()) {
            case SENSOR_BASED -> evaluateFromSensor(decision, execution);
            case ASSESSMENT_RESOLVED -> evaluateFromAssessments(decision);
            case HUMAN_CONFIRMED -> evaluateFromHumanReport(execution);
            case HYBRID -> {
                Evaluation sensor = evaluateFromSensor(decision, execution);
                yield sensor.result() == OutcomeResult.INCONCLUSIVE
                        ? evaluateFromHumanReport(execution)
                        : sensor;
            }
        };

        Outcome outcome = new Outcome();
        outcome.setCareLoopId(execution.getCareLoopId());
        outcome.setDecisionId(decision.getId());
        outcome.setCommandId(command.getId());
        outcome.setExecutionId(execution.getId());
        outcome.setResult(evaluation.result());
        outcome.setEvaluatedAt(now);
        outcome.setEvaluationWindowStart(schedule.getEvaluateAfter());
        outcome.setEvaluationWindowEnd(schedule.getWindowEnd());
        outcome.setEvidence(evaluation.evidence());
        outcome.setSummary(evaluation.summary());
        outcome.setEvaluatedBy(ActorType.DETERMINISTIC_ENGINE);

        Outcome saved = outcomeRepository.save(outcome);

        schedule.setCompletedAt(now);
        scheduleRepository.save(schedule);

        scopeService.recordScope(
                saved.getCareLoopId(), LoopRecordType.OUTCOME, saved.getId(), LoopScope.IN_SCOPE,
                "AUTOMATIC_OUTCOME_EVALUATION", "Outcome derived from an in-scope execution.",
                ActorType.DETERMINISTIC_ENGINE, null, now, null
        );

        LOGGER.info(
                "Outcome evaluated: id={} execution={} result={}",
                saved.getId(), execution.getId(), saved.getResult()
        );
        return Optional.of(saved);
    }

    private Evaluation evaluateFromSensor(Decision decision, Execution execution) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("evaluationMethod", "SENSOR_BASED");

        Object cropIdValue = decision.getParameters() == null ? null : decision.getParameters().get("cropId");
        if (!(cropIdValue instanceof Number cropNumber)) {
            evidence.put("reason", "NO_CROP_IN_DECISION_PARAMETERS");
            return new Evaluation(OutcomeResult.INCONCLUSIVE, evidence,
                    "No crop was identified on the decision, so sensor evidence could not be located.");
        }
        long cropId = cropNumber.longValue();
        evidence.put("cropId", cropId);

        Optional<CropSensorAssignment> assignment = assignmentService.findCurrentAssignmentForCrop(cropId);
        if (assignment.isEmpty()) {
            evidence.put("reason", "NO_SENSOR_ASSIGNED");
            return new Evaluation(OutcomeResult.INCONCLUSIVE, evidence,
                    "This crop has no soil probe, so the effect could not be measured. A manual observation "
                            + "is needed to judge it.");
        }

        String sensorId = assignment.get().getSensorId();
        evidence.put("sensorId", sensorId);

        List<SoilMoistureReadingEntity> after = soilMoistureReadingRepository
                .findAllBySensorIdAndReceivedAtAfterOrderByReceivedAtDesc(sensorId, execution.getCompletedAt());
        if (after.isEmpty()) {
            evidence.put("reason", "NO_READINGS_SINCE_EXECUTION");
            return new Evaluation(OutcomeResult.INCONCLUSIVE, evidence,
                    "No probe readings arrived after the work was done, so its effect is unknown.");
        }

        Optional<SensorCalibration> calibration = calibrationService.findCurrentCalibration(sensorId);
        if (calibration.isEmpty()) {
            evidence.put("reason", "CALIBRATION_REQUIRED");
            return new Evaluation(OutcomeResult.INCONCLUSIVE, evidence,
                    "The probe has no calibration, so its readings cannot be interpreted as a moisture index.");
        }

        SoilMoistureReadingEntity latest = after.get(0);
        SoilMoistureReadingEntity earliest = after.get(after.size() - 1);
        MoistureIndex latestIndex = calibrationService.calculateIndex(calibration.get(), latest.getRawAdc());
        MoistureIndex earliestIndex = calibrationService.calculateIndex(calibration.get(), earliest.getRawAdc());

        evidence.put("moistureIndexAfter", latestIndex.value());
        evidence.put("moistureIndexFirstReadingAfter", earliestIndex.value());
        evidence.put("readingsConsidered", after.size());
        evidence.put("latestReadingAt", String.valueOf(latest.getReceivedAt()));

        // For watering, the expected direction is wetter. A rise in the index
        // is the observable effect; no rise is a real, reportable failure.
        double change = latestIndex.value() - earliestIndex.value();
        evidence.put("moistureIndexChange", change);

        if (latestIndex.value() > earliestIndex.value()) {
            return new Evaluation(OutcomeResult.SUCCESS, evidence,
                    String.format("Soil moisture index rose to %.0f after the work was carried out.",
                            latestIndex.value()));
        }
        if (Math.abs(change) < 1.0) {
            return new Evaluation(OutcomeResult.PARTIAL, evidence,
                    String.format("Soil moisture index barely moved (%.0f), which is less response than expected.",
                            latestIndex.value()));
        }
        return new Evaluation(OutcomeResult.FAILED, evidence,
                String.format("Soil moisture index fell to %.0f rather than rising after the work.",
                        latestIndex.value()));
    }

    private Evaluation evaluateFromAssessments(Decision decision) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("evaluationMethod", "ASSESSMENT_RESOLVED");

        List<Long> assessmentIds = decisionService.assessmentIdsFor(decision.getId());
        if (assessmentIds.isEmpty()) {
            evidence.put("reason", "NO_LINKED_ASSESSMENTS");
            return new Evaluation(OutcomeResult.INCONCLUSIVE, evidence,
                    "No assessments were linked to this decision, so there is nothing to check for resolution.");
        }

        List<AssessmentEntity> assessments = assessmentIds.stream()
                .map(assessmentRepository::findById)
                .flatMap(Optional::stream)
                .toList();
        evidence.put("assessmentIds", assessmentIds);

        long stillActive = assessments.stream()
                .filter(a -> a.getStatus() == AssessmentStatus.ACTIVE)
                .count();
        evidence.put("assessmentsStillActive", stillActive);

        if (stillActive == 0) {
            return new Evaluation(OutcomeResult.SUCCESS, evidence,
                    "The condition that prompted this work has resolved.");
        }
        if (stillActive < assessments.size()) {
            return new Evaluation(OutcomeResult.PARTIAL, evidence,
                    "Some but not all of the conditions that prompted this work have resolved.");
        }
        return new Evaluation(OutcomeResult.FAILED, evidence,
                "The condition that prompted this work is still present.");
    }

    // The human already told us what happened when they recorded the
    // execution; that report is the evidence.
    private Evaluation evaluateFromHumanReport(Execution execution) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("evaluationMethod", "HUMAN_CONFIRMED");
        evidence.put("executionResult", execution.getResult().name());
        if (execution.getNotes() != null) {
            evidence.put("executionNotes", execution.getNotes());
        }

        return switch (execution.getResult()) {
            case COMPLETED -> new Evaluation(OutcomeResult.SUCCESS, evidence,
                    "The work was reported as completed.");
            case PARTIAL -> new Evaluation(OutcomeResult.PARTIAL, evidence,
                    "The work was reported as only partly done.");
            case FAILED -> new Evaluation(OutcomeResult.FAILED, evidence,
                    "The work was reported as not carried out.");
        };
    }

    // A human review appends an event. If it carries a corrected result, a new
    // superseding Outcome is created - the original is never rewritten.
    @Transactional
    public OutcomeReviewEvent recordReview(
            Long outcomeId,
            String reviewNote,
            boolean disputed,
            Map<String, Object> additionalEvidence,
            OutcomeResult correctedResult,
            ActorType actorType,
            String actorId,
            String requestId
    ) {
        Outcome outcome = outcomeRepository.findById(outcomeId)
                .orElseThrow(() -> new DomainValidationException("Unknown outcome: " + outcomeId));
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new DomainValidationException("reviewNote is required.");
        }

        Instant now = clock.instant();

        OutcomeReviewEvent review = new OutcomeReviewEvent();
        review.setOutcomeId(outcomeId);
        review.setReviewNote(reviewNote);
        review.setDisputed(disputed);
        review.setAdditionalEvidence(additionalEvidence);
        review.setActorType(actorType == null ? ActorType.HUMAN_VIA_AGENT : actorType);
        review.setActorId(actorId);
        review.setOccurredAt(now);
        review.setRequestId(requestId);

        if (correctedResult != null && correctedResult != outcome.getResult()) {
            Outcome corrected = new Outcome();
            corrected.setCareLoopId(outcome.getCareLoopId());
            corrected.setDecisionId(outcome.getDecisionId());
            corrected.setCommandId(outcome.getCommandId());
            corrected.setExecutionId(outcome.getExecutionId());
            corrected.setResult(correctedResult);
            corrected.setEvaluatedAt(now);
            corrected.setEvaluationWindowStart(outcome.getEvaluationWindowStart());
            corrected.setEvaluationWindowEnd(outcome.getEvaluationWindowEnd());
            corrected.setEvidence(additionalEvidence);
            corrected.setSummary("Corrected after human review: " + reviewNote);
            corrected.setEvaluatedBy(actorType == null ? ActorType.HUMAN_VIA_AGENT : actorType);
            corrected.setEvaluatedByActorId(actorId);
            corrected.setSupersedesOutcomeId(outcomeId);

            Outcome savedCorrection = outcomeRepository.save(corrected);
            review.setResultingOutcomeId(savedCorrection.getId());

            scopeService.recordScope(
                    savedCorrection.getCareLoopId(), LoopRecordType.OUTCOME, savedCorrection.getId(),
                    LoopScope.IN_SCOPE, "HUMAN_OUTCOME_CORRECTION",
                    "Outcome corrected by human review.",
                    review.getActorType(), actorId, now, requestId
            );
        }

        return reviewEventRepository.save(review);
    }

    public List<Outcome> forLoop(Long careLoopId) {
        return outcomeRepository.findAllByCareLoopIdOrderByEvaluatedAtDesc(careLoopId);
    }

    public List<OutcomeReviewEvent> reviewsFor(Long outcomeId) {
        return reviewEventRepository.findAllByOutcomeIdOrderByOccurredAtAsc(outcomeId);
    }

    public Long careLoopIdForOutcome(Long outcomeId) {
        return outcomeRepository.findById(outcomeId)
                .map(Outcome::getCareLoopId)
                .orElseThrow(() -> new DomainValidationException("Unknown outcome: " + outcomeId));
    }

    public List<Outcome> forLoopsSince(Instant since) {
        return outcomeRepository.findAllByEvaluatedAtAfterOrderByEvaluatedAtDesc(since);
    }

    public List<OutcomeEvaluationSchedule> pendingEvaluations() {
        return scheduleRepository.findAllByCompletedAtIsNull();
    }

    private record Evaluation(OutcomeResult result, Map<String, Object> evidence, String summary) {
    }
}
