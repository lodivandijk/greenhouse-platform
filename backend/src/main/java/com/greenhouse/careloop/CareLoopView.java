package com.greenhouse.careloop;

import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEvent;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEvent;
import com.greenhouse.careloop.execution.Execution;
import com.greenhouse.careloop.outcome.Outcome;
import com.greenhouse.careloop.outcome.OutcomeReviewEvent;
import com.greenhouse.careloop.scope.LoopRecordScopeEvent;

import java.time.Instant;
import java.util.List;

// The complete, resumable picture of one care loop: every record, every
// lifecycle event, the scope history and current effective scope, and what the
// human is expected to do next.
//
// Deliberately exhaustive - a fresh agent session with no prior context must
// be able to pick a loop up from this alone (ADR-021).
public record CareLoopView(
        Long id,
        CareLoopSubjectType primarySubjectType,
        String primarySubjectId,
        String conditionType,
        String correlationKey,
        CareLoopStatus status,
        String nextRequiredAction,
        Instant openedAt,
        Instant closedAt,
        ActorType createdBy,
        List<AssessmentResponse> assessments,
        List<DecisionView> decisions,
        Long effectiveDecisionId,
        List<CommandView> commands,
        List<Execution> executions,
        List<OutcomeView> outcomes,
        List<LoopRecordScopeEvent> scopeHistory,
        List<CareLoopStatusEvent> statusHistory
) {

    public record DecisionView(
            Decision decision,
            String currentState,
            boolean inScope,
            List<DecisionLifecycleEvent> lifecycle
    ) {
    }

    public record CommandView(
            Command command,
            String currentState,
            boolean inScope,
            List<CommandLifecycleEvent> lifecycle
    ) {
    }

    public record OutcomeView(
            Outcome outcome,
            List<OutcomeReviewEvent> reviews
    ) {
    }
}
