package com.greenhouse.careloop;

import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEventType;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.Execution;
import com.greenhouse.careloop.execution.ExecutionService;
import com.greenhouse.careloop.outcome.Outcome;
import com.greenhouse.careloop.outcome.OutcomeService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.ScopeService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Derives a loop's current state from its immutable records, on read.
//
// Computed rather than materialised: loop volumes here are a handful of crops,
// so a cached status column would be a second thing to keep correct for no
// measurable gain. The spec explicitly permits either.
@Service
public class CareLoopProjectionService {

    private final CareLoopRepository careLoopRepository;
    private final CareLoopStatusEventRepository statusEventRepository;
    private final DecisionService decisionService;
    private final CommandService commandService;
    private final ExecutionService executionService;
    private final OutcomeService outcomeService;
    private final ScopeService scopeService;

    public CareLoopProjectionService(
            CareLoopRepository careLoopRepository,
            CareLoopStatusEventRepository statusEventRepository,
            DecisionService decisionService,
            CommandService commandService,
            ExecutionService executionService,
            OutcomeService outcomeService,
            ScopeService scopeService
    ) {
        this.careLoopRepository = careLoopRepository;
        this.statusEventRepository = statusEventRepository;
        this.decisionService = decisionService;
        this.commandService = commandService;
        this.executionService = executionService;
        this.outcomeService = outcomeService;
        this.scopeService = scopeService;
    }

    public CareLoop requireLoop(Long careLoopId) {
        return careLoopRepository.findById(careLoopId)
                .orElseThrow(() -> new CareLoopNotFoundException(careLoopId));
    }

    // Walks the loop backwards from the furthest-progressed record: whatever
    // the loop is waiting on IS its status.
    public CareLoopStatus projectStatus(Long careLoopId) {
        CareLoop loop = requireLoop(careLoopId);
        if (loop.getClosedAt() != null) {
            return CareLoopStatus.CLOSED;
        }

        Optional<CareLoopStatus> explicitBlock = statusEventRepository
                .findAllByCareLoopIdOrderByOccurredAtAsc(careLoopId).stream()
                .reduce((first, second) -> second)
                .map(CareLoopStatusEvent::getStatus)
                .filter(status -> status == CareLoopStatus.BLOCKED);
        if (explicitBlock.isPresent()) {
            return CareLoopStatus.BLOCKED;
        }

        Optional<Decision> effective = effectiveDecision(careLoopId);
        if (effective.isEmpty()) {
            return CareLoopStatus.AWAITING_HUMAN_REVIEW;
        }

        Decision decision = effective.get();
        DecisionLifecycleEventType decisionState =
                decisionService.currentState(decision.getId()).orElse(DecisionLifecycleEventType.PROPOSED);

        if (decisionState == DecisionLifecycleEventType.PROPOSED) {
            return CareLoopStatus.AWAITING_DECISION_APPROVAL;
        }
        if (decisionState == DecisionLifecycleEventType.REJECTED) {
            // A rejected decision leaves the underlying condition unanswered.
            return CareLoopStatus.AWAITING_HUMAN_REVIEW;
        }

        Optional<Command> command = commandService.forLoop(careLoopId).stream()
                .filter(c -> c.getDecisionId().equals(decision.getId()))
                .findFirst();
        if (command.isEmpty()) {
            return CareLoopStatus.AWAITING_COMMAND_ACKNOWLEDGEMENT;
        }

        CommandLifecycleEventType commandState =
                commandService.currentState(command.get().getId()).orElse(CommandLifecycleEventType.ISSUED);

        if (commandState == CommandLifecycleEventType.DECLINED
                || commandState == CommandLifecycleEventType.CANCELLED
                || commandState == CommandLifecycleEventType.EXPIRED) {
            return CareLoopStatus.AWAITING_HUMAN_REVIEW;
        }

        // Checked before the acknowledgement state on purpose: someone may
        // simply do the work and report it without formally acknowledging
        // first, and a recorded execution is stronger evidence of progress
        // than a missing acknowledgement is of the lack of it.
        List<Execution> executions = executionService.forCommand(command.get().getId());
        if (!executions.isEmpty()) {
            List<Outcome> outcomes = outcomeService.forLoop(careLoopId);
            return outcomes.isEmpty() ? CareLoopStatus.EVALUATING_OUTCOME : CareLoopStatus.OPEN;
        }

        if (commandState == CommandLifecycleEventType.ISSUED) {
            return CareLoopStatus.AWAITING_COMMAND_ACKNOWLEDGEMENT;
        }

        return CareLoopStatus.AWAITING_EXECUTION;
    }

    // The decision that currently governs the loop: the newest one that has
    // not been superseded. Superseded decisions remain readable history.
    public Optional<Decision> effectiveDecision(Long careLoopId) {
        return decisionService.forLoop(careLoopId).stream()
                .filter(decision -> scopeService.isInScope(
                        careLoopId, LoopRecordType.DECISION, decision.getId()))
                .filter(decision -> decisionService.currentState(decision.getId())
                        .map(state -> state != DecisionLifecycleEventType.SUPERSEDED)
                        .orElse(true))
                .findFirst();
    }

    // What the human is expected to do next, in plain language, so a fresh
    // agent session can pick the loop up without inferring it.
    public String nextRequiredAction(Long careLoopId) {
        CareLoopStatus status = projectStatus(careLoopId);
        return switch (status) {
            case AWAITING_HUMAN_REVIEW ->
                    "Review the evidence and decide what to do (or propose a decision).";
            case AWAITING_DECISION_APPROVAL ->
                    "Approve or reject the proposed decision.";
            case AWAITING_COMMAND_ACKNOWLEDGEMENT ->
                    "Acknowledge, defer or decline the issued command.";
            case AWAITING_EXECUTION ->
                    "Carry out the command and record what was actually done.";
            case EVALUATING_OUTCOME ->
                    "Nothing right now - waiting for evidence to judge the result.";
            case BLOCKED -> "This loop is blocked and needs attention.";
            case CLOSED -> "Nothing - this loop is closed.";
            case OPEN -> "Nothing right now.";
        };
    }

    public List<CareLoop> openLoops() {
        return careLoopRepository.findAllByClosedAtIsNullOrderByOpenedAtDesc();
    }
}
