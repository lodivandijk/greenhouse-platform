package com.greenhouse.careloop;

import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEventType;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.ExecutionService;
import com.greenhouse.careloop.outcome.Outcome;
import com.greenhouse.careloop.outcome.OutcomeService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.ScopeService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Read-side composition for care loops. Kept separate from the write services
// so that query shaping never leaks into domain operations.
@Service
public class CareLoopQueryService {

    private final CareLoopRepository careLoopRepository;
    private final CareLoopAssessmentRepository careLoopAssessmentRepository;
    private final CareLoopStatusEventRepository statusEventRepository;
    private final CareLoopProjectionService projectionService;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentMapper assessmentMapper;
    private final DecisionService decisionService;
    private final CommandService commandService;
    private final ExecutionService executionService;
    private final OutcomeService outcomeService;
    private final ScopeService scopeService;

    public CareLoopQueryService(
            CareLoopRepository careLoopRepository,
            CareLoopAssessmentRepository careLoopAssessmentRepository,
            CareLoopStatusEventRepository statusEventRepository,
            CareLoopProjectionService projectionService,
            AssessmentRepository assessmentRepository,
            AssessmentMapper assessmentMapper,
            DecisionService decisionService,
            CommandService commandService,
            ExecutionService executionService,
            OutcomeService outcomeService,
            ScopeService scopeService
    ) {
        this.careLoopRepository = careLoopRepository;
        this.careLoopAssessmentRepository = careLoopAssessmentRepository;
        this.statusEventRepository = statusEventRepository;
        this.projectionService = projectionService;
        this.assessmentRepository = assessmentRepository;
        this.assessmentMapper = assessmentMapper;
        this.decisionService = decisionService;
        this.commandService = commandService;
        this.executionService = executionService;
        this.outcomeService = outcomeService;
        this.scopeService = scopeService;
    }

    public List<OpenCareLoopSummary> openLoops(CareLoopSubjectType subjectType, String subjectId) {
        return careLoopRepository.findAllByClosedAtIsNullOrderByOpenedAtDesc().stream()
                .filter(loop -> subjectType == null || loop.getPrimarySubjectType() == subjectType)
                .filter(loop -> subjectId == null || subjectId.equals(loop.getPrimarySubjectId()))
                .map(this::toSummary)
                .toList();
    }

    private OpenCareLoopSummary toSummary(CareLoop loop) {
        CareLoopStatus status = projectionService.projectStatus(loop.getId());
        Optional<Decision> effective = projectionService.effectiveDecision(loop.getId());

        Long pendingDecisionId = effective
                .filter(decision -> decisionService.currentState(decision.getId())
                        .map(state -> state == DecisionLifecycleEventType.PROPOSED)
                        .orElse(false))
                .map(Decision::getId)
                .orElse(null);

        Long pendingCommandId = commandService.forLoop(loop.getId()).stream()
                .filter(command -> {
                    CommandLifecycleEventType state =
                            commandService.currentState(command.getId()).orElse(CommandLifecycleEventType.ISSUED);
                    return state == CommandLifecycleEventType.ISSUED
                            || state == CommandLifecycleEventType.ACKNOWLEDGED
                            || state == CommandLifecycleEventType.DEFERRED;
                })
                .map(Command::getId)
                .findFirst()
                .orElse(null);

        return new OpenCareLoopSummary(
                loop.getId(),
                loop.getPrimarySubjectType(),
                loop.getPrimarySubjectId(),
                loop.getConditionType(),
                status,
                projectionService.nextRequiredAction(loop.getId()),
                loop.getOpenedAt(),
                pendingDecisionId,
                pendingCommandId
        );
    }

    public CareLoopView loopDetail(Long careLoopId) {
        CareLoop loop = projectionService.requireLoop(careLoopId);

        List<AssessmentResponse> assessments = careLoopAssessmentRepository.findAllByCareLoopId(careLoopId).stream()
                .map(CareLoopAssessment::getAssessmentId)
                .map(assessmentRepository::findById)
                .flatMap(Optional::stream)
                .map(assessmentMapper::toResponse)
                .toList();

        List<CareLoopView.DecisionView> decisions = decisionService.forLoop(careLoopId).stream()
                .map(decision -> new CareLoopView.DecisionView(
                        decision,
                        decisionService.currentState(decision.getId())
                                .map(Enum::name).orElse("UNKNOWN"),
                        scopeService.isInScope(careLoopId, LoopRecordType.DECISION, decision.getId()),
                        decisionService.history(decision.getId())
                ))
                .toList();

        List<CareLoopView.CommandView> commands = commandService.forLoop(careLoopId).stream()
                .map(command -> new CareLoopView.CommandView(
                        command,
                        commandService.currentState(command.getId())
                                .map(Enum::name).orElse("UNKNOWN"),
                        scopeService.isInScope(careLoopId, LoopRecordType.COMMAND, command.getId()),
                        commandService.history(command.getId())
                ))
                .toList();

        List<CareLoopView.OutcomeView> outcomes = outcomeService.forLoop(careLoopId).stream()
                .map(outcome -> new CareLoopView.OutcomeView(
                        outcome, outcomeService.reviewsFor(outcome.getId())))
                .toList();

        return new CareLoopView(
                loop.getId(),
                loop.getPrimarySubjectType(),
                loop.getPrimarySubjectId(),
                loop.getConditionType(),
                loop.getCorrelationKey(),
                projectionService.projectStatus(careLoopId),
                projectionService.nextRequiredAction(careLoopId),
                loop.getOpenedAt(),
                loop.getClosedAt(),
                loop.getCreatedBy(),
                assessments,
                decisions,
                projectionService.effectiveDecision(careLoopId).map(Decision::getId).orElse(null),
                commands,
                executionService.forLoop(careLoopId),
                outcomes,
                scopeService.scopeHistory(careLoopId),
                statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(careLoopId)
        );
    }

    public List<Outcome> recentOutcomes(java.time.Instant since) {
        return outcomeService.forLoopsSince(since);
    }
}
