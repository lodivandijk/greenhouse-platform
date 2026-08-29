package com.greenhouse.careloop;

import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandRepository;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEventType;
import com.greenhouse.careloop.decision.DecisionRepository;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.Execution;
import com.greenhouse.careloop.execution.ExecutionRepository;
import com.greenhouse.careloop.execution.ExecutionResult;
import com.greenhouse.careloop.execution.ExecutionService;
import com.greenhouse.careloop.outcome.OutcomeEvaluationScheduleRepository;
import com.greenhouse.careloop.outcome.OutcomeRepository;
import com.greenhouse.careloop.outcome.OutcomeService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Exercises the real loop transitions against a real database. Not
// @Transactional: these services span several transactions of their own and
// the point is to observe what genuinely persists, so rows are cleaned up
// explicitly instead.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false"
})
class CareLoopLifecycleTest {

    @Autowired private CareLoopRepository careLoopRepository;
    @Autowired private CareLoopStatusEventRepository statusEventRepository;
    @Autowired private CareLoopService careLoopService;
    @Autowired private CareLoopProjectionService projectionService;
    @Autowired private DecisionService decisionService;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private CommandService commandService;
    @Autowired private CommandRepository commandRepository;
    @Autowired private ExecutionService executionService;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private OutcomeService outcomeService;
    @Autowired private OutcomeRepository outcomeRepository;
    @Autowired private OutcomeEvaluationScheduleRepository scheduleRepository;
    @Autowired private ScopeService scopeService;
    @Autowired private com.greenhouse.careloop.scope.LoopRecordScopeEventRepository scopeEventRepository;
    @Autowired private com.greenhouse.careloop.decision.DecisionLifecycleEventRepository decisionEventRepository;
    @Autowired private com.greenhouse.careloop.command.CommandLifecycleEventRepository commandEventRepository;
    @Autowired private com.greenhouse.careloop.decision.DecisionAssessmentRepository decisionAssessmentRepository;
    @Autowired private com.greenhouse.careloop.decision.DecisionGoalRepository decisionGoalRepository;
    @Autowired private com.greenhouse.careloop.outcome.OutcomeReviewEventRepository outcomeReviewEventRepository;

    private CareLoop loop;

    @BeforeEach
    void createLoop() {
        CareLoop newLoop = new CareLoop();
        newLoop.setPrimarySubjectType(CareLoopSubjectType.CROP);
        newLoop.setPrimarySubjectId("8");
        newLoop.setConditionType("CROP_SOIL_MOISTURE_LOW");
        newLoop.setCorrelationKey("TEST:CROP:8:SOIL_MOISTURE_LOW:" + System.nanoTime());
        newLoop.setOpenedAt(Instant.now());
        newLoop.setCreatedBy(ActorType.DETERMINISTIC_ENGINE);
        loop = careLoopRepository.save(newLoop);
    }

    @AfterEach
    void cleanUp() {
        if (loop == null) {
            return;
        }
        // Children before parents throughout - these tables have real foreign
        // keys and nothing here cascades.
        outcomeRepository.findAllByCareLoopIdOrderByEvaluatedAtDesc(loop.getId()).forEach(outcome ->
                outcomeReviewEventRepository.deleteAll(
                        outcomeReviewEventRepository.findAllByOutcomeIdOrderByOccurredAtAsc(outcome.getId())));
        // A corrected outcome references the one it supersedes, so clear the
        // superseding rows first.
        List<com.greenhouse.careloop.outcome.Outcome> outcomes =
                outcomeRepository.findAllByCareLoopIdOrderByEvaluatedAtDesc(loop.getId());
        outcomes.stream().filter(o -> o.getSupersedesOutcomeId() != null).forEach(outcomeRepository::delete);
        outcomeRepository.deleteAll(outcomeRepository.findAllByCareLoopIdOrderByEvaluatedAtDesc(loop.getId()));

        List<Execution> executions = executionRepository.findAllByCareLoopIdOrderByCompletedAtDesc(loop.getId());
        executions.forEach(execution -> scheduleRepository.findByExecutionId(execution.getId())
                .ifPresent(scheduleRepository::delete));
        executions.stream().filter(e -> e.getCorrectsExecutionId() != null).forEach(executionRepository::delete);
        executionRepository.deleteAll(
                executionRepository.findAllByCareLoopIdOrderByCompletedAtDesc(loop.getId()));

        commandRepository.findAllByCareLoopIdOrderByIssuedAtDesc(loop.getId()).forEach(command ->
                commandEventRepository.deleteAll(
                        commandEventRepository.findAllByCommandIdOrderByOccurredAtAsc(command.getId())));
        commandRepository.deleteAll(commandRepository.findAllByCareLoopIdOrderByIssuedAtDesc(loop.getId()));

        List<Decision> decisions = decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(loop.getId());
        decisions.forEach(decision -> {
            decisionEventRepository.deleteAll(
                    decisionEventRepository.findAllByDecisionIdOrderByOccurredAtAsc(decision.getId()));
            decisionAssessmentRepository.deleteAll(
                    decisionAssessmentRepository.findAllByDecisionId(decision.getId()));
            decisionGoalRepository.deleteAll(decisionGoalRepository.findAllByDecisionId(decision.getId()));
        });
        // A replacement decision references the one it supersedes.
        decisions.stream().filter(d -> d.getSupersedesDecisionId() != null).forEach(decisionRepository::delete);
        decisionRepository.deleteAll(
                decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(loop.getId()));
        scopeEventRepository.deleteAll(scopeEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()));
        statusEventRepository.deleteAll(statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()));
        careLoopRepository.delete(loop);
    }

    private Decision proposeWatering(int quantityMl) {
        return decisionService.propose(
                loop.getId(), CommandType.WATER_CROP,
                Map.of("cropId", 8, "quantity", quantityMl, "unit", "ml"),
                "Soil moisture index is below the dry threshold.",
                List.of(), List.of(),
                "Soil moisture index rises above the dry threshold.",
                null, null, null,
                "Moisture index above 30 within 12 hours.",
                ActorType.AGENT, "claude", null, null
        );
    }

    @Test
    void proposingADecisionDoesNotIssueACommand() {
        Decision decision = proposeWatering(200);

        assertThat(commandRepository.findByDecisionId(decision.getId())).isEmpty();
        assertThat(decisionService.currentState(decision.getId()))
                .contains(DecisionLifecycleEventType.PROPOSED);
        assertThat(projectionService.projectStatus(loop.getId()))
                .isEqualTo(CareLoopStatus.AWAITING_DECISION_APPROVAL);
    }

    @Test
    void approvalIssuesExactlyOneCommand() {
        Decision decision = proposeWatering(200);

        CareLoopService.DecisionResponse response = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", "req-approve-1"
        );

        assertThat(response.issuedCommand()).isPresent();
        Command command = response.issuedCommand().get();
        assertThat(command.getCommandType()).isEqualTo(CommandType.WATER_CROP);
        assertThat(command.getTargetType().name()).isEqualTo("HUMAN");
        assertThat(commandRepository.findAllByCareLoopIdOrderByIssuedAtDesc(loop.getId())).hasSize(1);
    }

    @Test
    void rejectionIssuesNoCommand() {
        Decision decision = proposeWatering(200);

        CareLoopService.DecisionResponse response = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.REJECTED, "Soil felt damp by hand.",
                ActorType.HUMAN_VIA_AGENT, "lodi", "req-reject-1"
        );

        assertThat(response.issuedCommand()).isEmpty();
        assertThat(commandRepository.findAllByCareLoopIdOrderByIssuedAtDesc(loop.getId())).isEmpty();
    }

    @Test
    void aDecisionCannotBeApprovedTwice() {
        Decision decision = proposeWatering(200);
        careLoopService.respondToDecision(decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", "req-1");

        assertThatThrownBy(() -> careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", "req-2"))
                .isInstanceOf(InvalidLoopTransitionException.class)
                .hasMessageContaining("already been APPROVED");
    }

    @Test
    void aReplacementDecisionPreservesTheOriginalAndOnlyTheReplacementCanBeApproved() {
        Decision original = proposeWatering(300);
        Decision replacement = decisionService.propose(
                loop.getId(), CommandType.WATER_CROP,
                Map.of("cropId", 8, "quantity", 200, "unit", "ml"),
                "User asked for less water.",
                List.of(), List.of(),
                "Soil moisture index rises above the dry threshold.",
                null, null, null, "Moisture index above 30.",
                ActorType.AGENT, "claude", original.getId(), null
        );

        // The original is untouched and still readable.
        Decision reloadedOriginal = decisionRepository.findById(original.getId()).orElseThrow();
        assertThat(reloadedOriginal.getParameters()).containsEntry("quantity", 300);
        assertThat(decisionService.currentState(original.getId()))
                .contains(DecisionLifecycleEventType.SUPERSEDED);

        assertThatThrownBy(() -> careLoopService.respondToDecision(
                original.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null))
                .isInstanceOf(InvalidLoopTransitionException.class)
                .hasMessageContaining("superseded");

        assertThat(projectionService.effectiveDecision(loop.getId()))
                .get().extracting(Decision::getId).isEqualTo(replacement.getId());
    }

    @Test
    void executionPreservesRequestedAndActualQuantitiesSeparately() {
        Decision decision = proposeWatering(300);
        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();

        commandService.recordResponse(command.getId(), CommandLifecycleEventType.ACKNOWLEDGED,
                null, null, ActorType.HUMAN_VIA_AGENT, "lodi", null);

        Execution execution = executionService.record(
                command.getId(), ExecutionResult.COMPLETED,
                Map.of("actualQuantity", 200, "actualUnit", "ml"),
                ActorType.HUMAN_VIA_AGENT, "lodi",
                null, Instant.now(), "Poured less - pot was already damp.", null, null
        );

        // Requested lives on the command, actual on the execution: both facts
        // survive rather than one overwriting the other.
        assertThat(command.getParameters()).containsEntry("quantity", 300);
        assertThat(execution.getActualParameters()).containsEntry("actualQuantity", 200);
    }

    @Test
    void recordingAnExecutionSchedulesItsOutcomeEvaluation() {
        Decision decision = proposeWatering(200);
        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();

        Execution execution = executionService.record(
                command.getId(), ExecutionResult.COMPLETED, Map.of("actualQuantity", 200, "actualUnit", "ml"),
                ActorType.HUMAN_VIA_AGENT, "lodi", null, Instant.now(), null, null, null
        );

        assertThat(scheduleRepository.findByExecutionId(execution.getId())).isPresent();
        assertThat(projectionService.projectStatus(loop.getId()))
                .isEqualTo(CareLoopStatus.EVALUATING_OUTCOME);
    }

    @Test
    void outcomeIsNotEvaluatedBeforeItsWindowElapses() {
        Decision decision = proposeWatering(200);
        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();
        executionService.record(command.getId(), ExecutionResult.COMPLETED,
                Map.of("actualQuantity", 200, "actualUnit", "ml"),
                ActorType.HUMAN_VIA_AGENT, "lodi", null, Instant.now(), null, null, null);

        // WATER_CROP defaults to a 2 hour delay, so nothing is due yet.
        List<com.greenhouse.careloop.outcome.Outcome> evaluated = outcomeService.evaluateDueOutcomes();

        assertThat(evaluated).noneMatch(o -> o.getCareLoopId().equals(loop.getId()));
    }

    @Test
    void anExecutionCannotBeRecordedAgainstADeclinedCommand() {
        Decision decision = proposeWatering(200);
        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();

        commandService.recordResponse(command.getId(), CommandLifecycleEventType.DECLINED,
                "Not doing this.", null, ActorType.HUMAN_VIA_AGENT, "lodi", null);

        assertThatThrownBy(() -> executionService.record(
                command.getId(), ExecutionResult.COMPLETED, Map.of(),
                ActorType.HUMAN_VIA_AGENT, "lodi", null, Instant.now(), null, null, null))
                .isInstanceOf(InvalidLoopTransitionException.class);
    }

    @Test
    void recordsAreAutomaticallyInScopeWithoutHumanAdministration() {
        Decision decision = proposeWatering(200);

        assertThat(scopeService.isInScope(loop.getId(), LoopRecordType.DECISION, decision.getId())).isTrue();

        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();

        assertThat(scopeService.isInScope(loop.getId(), LoopRecordType.COMMAND, command.getId())).isTrue();
    }

    @Test
    void aHumanCanExcludeARecordWithAReasonWithoutDeletingIt() {
        Decision decision = proposeWatering(200);

        careLoopService.recordScopeOverride(
                loop.getId(), LoopRecordType.DECISION, decision.getId(), LoopScope.OUT_OF_SCOPE,
                "INVALID_SENSOR_POSITION", "Probe was out of the pot when this was proposed.",
                ActorType.HUMAN_VIA_AGENT, "lodi", null
        );

        assertThat(scopeService.isInScope(loop.getId(), LoopRecordType.DECISION, decision.getId())).isFalse();
        // The decision itself is untouched - scope is a relationship, not a delete.
        assertThat(decisionRepository.findById(decision.getId())).isPresent();
        // Scope history retains both the automatic and the override event.
        assertThat(scopeService.scopeHistory(loop.getId())).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void scopeOverrideRequiresAReason() {
        Decision decision = proposeWatering(200);

        assertThatThrownBy(() -> careLoopService.recordScopeOverride(
                loop.getId(), LoopRecordType.DECISION, decision.getId(), LoopScope.OUT_OF_SCOPE,
                "  ", "no code given", ActorType.HUMAN_VIA_AGENT, "lodi", null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void catalogueRejectsAnUnknownParameterAndAQuantityWithoutAUnit() {
        assertThatThrownBy(() -> decisionService.propose(
                loop.getId(), CommandType.WATER_CROP,
                Map.of("cropId", 8, "quantity", 200, "unit", "ml", "sneaky", "value"),
                "r", List.of(), List.of(), "e", null, null, null, null,
                ActorType.AGENT, "claude", null, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("does not accept parameter");

        assertThatThrownBy(() -> decisionService.propose(
                loop.getId(), CommandType.WATER_CROP,
                Map.of("cropId", 8, "quantity", 200),
                "r", List.of(), List.of(), "e", null, null, null, null,
                ActorType.AGENT, "claude", null, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("unit");
    }

    @Test
    void projectionReportsWhatTheHumanMustDoNext() {
        Decision decision = proposeWatering(200);
        assertThat(projectionService.nextRequiredAction(loop.getId()))
                .contains("Approve or reject");

        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();
        assertThat(projectionService.nextRequiredAction(loop.getId()))
                .contains("Acknowledge");

        commandService.recordResponse(command.getId(), CommandLifecycleEventType.ACKNOWLEDGED,
                null, null, ActorType.HUMAN_VIA_AGENT, "lodi", null);
        assertThat(projectionService.nextRequiredAction(loop.getId()))
                .contains("record what was actually done");
    }

    @Test
    void aClosedLoopRejectsNewDecisions() {
        loop.setClosedAt(Instant.now());
        careLoopRepository.save(loop);

        assertThatThrownBy(() -> proposeWatering(200))
                .isInstanceOf(InvalidLoopTransitionException.class)
                .hasMessageContaining("closed");

        loop.setClosedAt(null);
        careLoopRepository.save(loop);
    }

    @Test
    void anExecutionCorrectionPreservesTheOriginalExecution() {
        Decision decision = proposeWatering(200);
        Command command = careLoopService.respondToDecision(
                decision.getId(), DecisionLifecycleEventType.APPROVED, null,
                ActorType.HUMAN_VIA_AGENT, "lodi", null).issuedCommand().orElseThrow();

        Execution first = executionService.record(
                command.getId(), ExecutionResult.COMPLETED, Map.of("actualQuantity", 500, "actualUnit", "ml"),
                ActorType.HUMAN_VIA_AGENT, "lodi", null, Instant.now(), "typo", null, null);

        Execution correction = executionService.record(
                command.getId(), ExecutionResult.COMPLETED, Map.of("actualQuantity", 200, "actualUnit", "ml"),
                ActorType.HUMAN_VIA_AGENT, "lodi", null, Instant.now(), "actually 200ml", first.getId(), null);

        Optional<Execution> reloadedFirst = executionRepository.findById(first.getId());
        assertThat(reloadedFirst).isPresent();
        assertThat(reloadedFirst.get().getActualParameters()).containsEntry("actualQuantity", 500);
        assertThat(correction.getCorrectsExecutionId()).isEqualTo(first.getId());
    }
}
