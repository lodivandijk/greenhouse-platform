package com.greenhouse.careloop.decision;

import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopNotFoundException;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.InvalidLoopTransitionException;
import com.greenhouse.careloop.command.catalogue.CommandCatalogue;
import com.greenhouse.careloop.command.catalogue.CommandDefinition;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.common.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DecisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionRepository decisionRepository;
    private final DecisionLifecycleEventRepository lifecycleEventRepository;
    private final DecisionAssessmentRepository decisionAssessmentRepository;
    private final DecisionGoalRepository decisionGoalRepository;
    private final CareLoopRepository careLoopRepository;
    private final AssessmentRepository assessmentRepository;
    private final CommandCatalogue commandCatalogue;
    private final ScopeService scopeService;
    private final Clock clock;

    public DecisionService(
            DecisionRepository decisionRepository,
            DecisionLifecycleEventRepository lifecycleEventRepository,
            DecisionAssessmentRepository decisionAssessmentRepository,
            DecisionGoalRepository decisionGoalRepository,
            CareLoopRepository careLoopRepository,
            AssessmentRepository assessmentRepository,
            CommandCatalogue commandCatalogue,
            ScopeService scopeService,
            Clock clock
    ) {
        this.decisionRepository = decisionRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.decisionAssessmentRepository = decisionAssessmentRepository;
        this.decisionGoalRepository = decisionGoalRepository;
        this.careLoopRepository = careLoopRepository;
        this.assessmentRepository = assessmentRepository;
        this.commandCatalogue = commandCatalogue;
        this.scopeService = scopeService;
        this.clock = clock;
    }

    // Proposing a decision does NOT issue a command. Nothing happens until a
    // human approves it (ADR-021).
    @Transactional
    public Decision propose(
            Long careLoopId,
            CommandType actionType,
            Map<String, Object> parameters,
            String rationale,
            List<Long> assessmentIds,
            List<Long> goalIds,
            String expectedEffect,
            OutcomeEvaluationMethod evaluationMethod,
            Long evaluationDelaySeconds,
            Long evaluationWindowSeconds,
            String successCriteria,
            ActorType proposedBy,
            String proposedByActorId,
            Long supersedesDecisionId,
            String requestId
    ) {
        CareLoop loop = careLoopRepository.findById(careLoopId)
                .orElseThrow(() -> new CareLoopNotFoundException(careLoopId));
        if (loop.getClosedAt() != null) {
            throw new InvalidLoopTransitionException(
                    "Care loop " + careLoopId + " is closed and cannot accept new decisions.");
        }
        if (actionType == null) {
            throw new DomainValidationException("actionType is required.");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new DomainValidationException("rationale is required - a decision must record why.");
        }
        if (expectedEffect == null || expectedEffect.isBlank()) {
            throw new DomainValidationException(
                    "expectedEffect is required - an outcome cannot be judged without it.");
        }

        commandCatalogue.validateParameters(actionType, parameters);
        CommandDefinition definition = commandCatalogue.definitionFor(actionType);

        if (assessmentIds != null) {
            for (Long assessmentId : assessmentIds) {
                if (!assessmentRepository.existsById(assessmentId)) {
                    throw new DomainValidationException("Unknown assessment: " + assessmentId);
                }
            }
        }

        if (supersedesDecisionId != null) {
            Decision previous = decisionRepository.findById(supersedesDecisionId)
                    .orElseThrow(() -> new DomainValidationException(
                            "Unknown decision to supersede: " + supersedesDecisionId));
            if (!previous.getCareLoopId().equals(careLoopId)) {
                throw new DomainValidationException(
                        "Decision " + supersedesDecisionId + " belongs to a different care loop.");
            }
        }

        Instant now = clock.instant();

        Decision decision = new Decision();
        decision.setCareLoopId(careLoopId);
        decision.setActionType(actionType);
        decision.setParameters(parameters == null ? Map.of() : parameters);
        decision.setRationale(rationale);
        decision.setExpectedEffect(expectedEffect);
        decision.setEvaluationMethod(
                evaluationMethod == null ? definition.defaultEvaluationMethod() : evaluationMethod);
        decision.setEvaluationDelaySeconds(evaluationDelaySeconds == null
                ? definition.defaultEvaluationDelay().getSeconds() : evaluationDelaySeconds);
        decision.setEvaluationWindowSeconds(evaluationWindowSeconds == null
                ? definition.defaultEvaluationWindow().getSeconds() : evaluationWindowSeconds);
        decision.setSuccessCriteria(successCriteria);
        decision.setProposedBy(proposedBy == null ? ActorType.AGENT : proposedBy);
        decision.setProposedByActorId(proposedByActorId);
        decision.setProposedAt(now);
        decision.setSupersedesDecisionId(supersedesDecisionId);

        Decision saved = decisionRepository.save(decision);

        if (assessmentIds != null) {
            for (Long assessmentId : assessmentIds) {
                decisionAssessmentRepository.save(new DecisionAssessment(saved.getId(), assessmentId));
            }
        }
        if (goalIds != null) {
            for (Long goalId : goalIds) {
                decisionGoalRepository.save(new DecisionGoal(saved.getId(), goalId));
            }
        }

        lifecycleEventRepository.save(new DecisionLifecycleEvent(
                saved.getId(), DecisionLifecycleEventType.PROPOSED, null,
                saved.getProposedBy(), proposedByActorId, now, requestId
        ));

        // The superseded decision is preserved intact; only an event marks it.
        if (supersedesDecisionId != null) {
            lifecycleEventRepository.save(new DecisionLifecycleEvent(
                    supersedesDecisionId, DecisionLifecycleEventType.SUPERSEDED,
                    "Replaced by decision " + saved.getId() + ".",
                    saved.getProposedBy(), proposedByActorId, now, requestId
            ));
        }

        scopeService.recordScope(
                careLoopId, LoopRecordType.DECISION, saved.getId(), LoopScope.IN_SCOPE,
                "AUTOMATIC_DECISION_PROPOSAL", "Decision proposed for this loop.",
                saved.getProposedBy(), proposedByActorId, now, requestId
        );

        LOGGER.info("Decision proposed: id={} loop={} action={}", saved.getId(), careLoopId, actionType);
        return saved;
    }

    public Decision requireDecision(Long decisionId) {
        return decisionRepository.findById(decisionId)
                .orElseThrow(() -> new DomainValidationException("Unknown decision: " + decisionId));
    }

    public Optional<DecisionLifecycleEventType> currentState(Long decisionId) {
        return lifecycleEventRepository.findFirstByDecisionIdOrderByOccurredAtDescIdDesc(decisionId)
                .map(DecisionLifecycleEvent::getEventType);
    }

    public boolean isApproved(Long decisionId) {
        return currentState(decisionId)
                .map(state -> state == DecisionLifecycleEventType.APPROVED)
                .orElse(false);
    }

    public List<DecisionLifecycleEvent> history(Long decisionId) {
        return lifecycleEventRepository.findAllByDecisionIdOrderByOccurredAtAsc(decisionId);
    }

    public List<Decision> forLoop(Long careLoopId) {
        return decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(careLoopId);
    }

    public List<Long> assessmentIdsFor(Long decisionId) {
        return decisionAssessmentRepository.findAllByDecisionId(decisionId).stream()
                .map(DecisionAssessment::getAssessmentId)
                .toList();
    }

    // Records approval or rejection as an appended event. Callers are
    // responsible for having obtained explicit human confirmation first; the
    // MCP layer enforces that contract in its tool description and passes
    // HUMAN_VIA_AGENT.
    @Transactional
    public DecisionLifecycleEvent recordResponse(
            Long decisionId,
            DecisionLifecycleEventType response,
            String reasonText,
            ActorType actorType,
            String actorId,
            String requestId
    ) {
        if (response != DecisionLifecycleEventType.APPROVED
                && response != DecisionLifecycleEventType.REJECTED) {
            throw new DomainValidationException(
                    "response must be APPROVED or REJECTED; PROPOSED and SUPERSEDED are recorded by the system.");
        }

        requireDecision(decisionId);

        DecisionLifecycleEventType state = currentState(decisionId)
                .orElseThrow(() -> new InvalidLoopTransitionException(
                        "Decision " + decisionId + " has no lifecycle history."));

        if (state == DecisionLifecycleEventType.SUPERSEDED) {
            throw new InvalidLoopTransitionException(
                    "Decision " + decisionId + " has been superseded by a replacement and can no longer be "
                            + "approved or rejected. Respond to the replacement instead.");
        }
        if (state == DecisionLifecycleEventType.APPROVED || state == DecisionLifecycleEventType.REJECTED) {
            throw new InvalidLoopTransitionException(
                    "Decision " + decisionId + " has already been " + state + ".");
        }

        DecisionLifecycleEvent event = lifecycleEventRepository.save(new DecisionLifecycleEvent(
                decisionId, response, reasonText, actorType, actorId, clock.instant(), requestId
        ));

        LOGGER.info("Decision {}: id={} actor={}", response, decisionId, actorType);
        return event;
    }
}
