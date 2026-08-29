package com.greenhouse.careloop.execution;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.InvalidLoopTransitionException;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.outcome.OutcomeEvaluationSchedule;
import com.greenhouse.careloop.outcome.OutcomeEvaluationScheduleRepository;
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

@Service
public class ExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executionRepository;
    private final OutcomeEvaluationScheduleRepository scheduleRepository;
    private final CommandService commandService;
    private final DecisionService decisionService;
    private final ScopeService scopeService;
    private final Clock clock;

    public ExecutionService(
            ExecutionRepository executionRepository,
            OutcomeEvaluationScheduleRepository scheduleRepository,
            CommandService commandService,
            DecisionService decisionService,
            ScopeService scopeService,
            Clock clock
    ) {
        this.executionRepository = executionRepository;
        this.scheduleRepository = scheduleRepository;
        this.commandService = commandService;
        this.decisionService = decisionService;
        this.scopeService = scopeService;
        this.clock = clock;
    }

    // Records what actually happened. Recording an execution and scheduling
    // its outcome evaluation are one transaction, so an execution can never
    // exist without something eventually judging it.
    @Transactional
    public Execution record(
            Long commandId,
            ExecutionResult result,
            Map<String, Object> actualParameters,
            ActorType performedBy,
            String performedByActorId,
            Instant startedAt,
            Instant completedAt,
            String notes,
            Long correctsExecutionId,
            String requestId
    ) {
        Command command = commandService.requireCommand(commandId);

        if (result == null) {
            throw new DomainValidationException("result is required (COMPLETED, PARTIAL or FAILED).");
        }

        CommandLifecycleEventType state = commandService.currentState(commandId).orElse(null);
        if (state == CommandLifecycleEventType.DECLINED || state == CommandLifecycleEventType.CANCELLED) {
            throw new InvalidLoopTransitionException(
                    "Command " + commandId + " is " + state + "; an execution cannot be recorded against it.");
        }

        if (correctsExecutionId != null) {
            Execution corrected = executionRepository.findById(correctsExecutionId)
                    .orElseThrow(() -> new DomainValidationException(
                            "Unknown execution to correct: " + correctsExecutionId));
            if (!corrected.getCommandId().equals(commandId)) {
                throw new DomainValidationException(
                        "Execution " + correctsExecutionId + " belongs to a different command.");
            }
        }

        Instant now = clock.instant();
        Instant completed = completedAt == null ? now : completedAt;

        if (startedAt != null && startedAt.isAfter(completed)) {
            throw new DomainValidationException("startedAt must not be after completedAt.");
        }

        Execution execution = new Execution();
        execution.setCareLoopId(command.getCareLoopId());
        execution.setCommandId(commandId);
        execution.setResult(result);
        execution.setActualParameters(actualParameters);
        execution.setPerformedBy(performedBy == null ? ActorType.HUMAN_VIA_AGENT : performedBy);
        execution.setPerformedByActorId(performedByActorId);
        execution.setStartedAt(startedAt);
        execution.setCompletedAt(completed);
        execution.setNotes(notes);
        execution.setRecordedAt(now);
        execution.setRequestId(requestId);
        execution.setCorrectsExecutionId(correctsExecutionId);

        Execution saved = executionRepository.save(execution);

        scopeService.recordScope(
                command.getCareLoopId(), LoopRecordType.EXECUTION, saved.getId(), LoopScope.IN_SCOPE,
                "AUTOMATIC_EXECUTION_RECORD", "Execution recorded against an in-scope command.",
                saved.getPerformedBy(), performedByActorId, now, requestId
        );

        scheduleOutcomeEvaluation(saved, command, now);

        LOGGER.info(
                "Execution recorded: id={} command={} result={} actor={}",
                saved.getId(), commandId, result, saved.getPerformedBy()
        );
        return saved;
    }

    private void scheduleOutcomeEvaluation(Execution execution, Command command, Instant now) {
        Decision decision = decisionService.requireDecision(command.getDecisionId());

        Instant evaluateAfter = execution.getCompletedAt().plus(decision.evaluationDelay());
        Instant windowEnd = evaluateAfter.plus(decision.evaluationWindow());

        scheduleRepository.save(new OutcomeEvaluationSchedule(
                execution.getId(), execution.getCareLoopId(), evaluateAfter, windowEnd, now
        ));
    }

    public List<Execution> forLoop(Long careLoopId) {
        return executionRepository.findAllByCareLoopIdOrderByCompletedAtDesc(careLoopId);
    }

    public List<Execution> forCommand(Long commandId) {
        return executionRepository.findAllByCommandIdOrderByCompletedAtDesc(commandId);
    }
}
