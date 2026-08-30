package com.greenhouse.notification;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.CareLoopStatusEventRepository;
import com.greenhouse.careloop.CareLoopSubjectType;
import com.greenhouse.careloop.decision.DecisionAssessmentRepository;
import com.greenhouse.careloop.decision.DecisionGoalRepository;
import com.greenhouse.careloop.decision.DecisionLifecycleEventRepository;
import com.greenhouse.careloop.decision.DecisionRepository;
import com.greenhouse.careloop.scope.LoopRecordScopeEventRepository;

import java.time.Instant;

// Shared fixture helpers. Kept as static methods rather than a base class so a
// test reads top-to-bottom without inherited setup happening off-screen.
final class NotificationTestSupport {

    private NotificationTestSupport() {
    }

    static CareLoop openLoop(CareLoopRepository repository, String conditionType) {
        CareLoop loop = new CareLoop();
        loop.setPrimarySubjectType(CareLoopSubjectType.CROP);
        loop.setPrimarySubjectId("8");
        loop.setConditionType(conditionType);
        loop.setCorrelationKey("NOTIFICATION-TEST:" + conditionType + ":" + System.nanoTime());
        loop.setOpenedAt(Instant.now());
        loop.setCreatedBy(ActorType.DETERMINISTIC_ENGINE);
        return repository.save(loop);
    }

    // Children before parents - these tables have real foreign keys and nothing
    // cascades.
    static void deleteLoop(
            Long loopId,
            CareLoopRepository careLoopRepository,
            CareLoopStatusEventRepository statusEventRepository,
            LoopRecordScopeEventRepository scopeEventRepository,
            DecisionRepository decisionRepository,
            DecisionLifecycleEventRepository decisionEventRepository,
            DecisionAssessmentRepository decisionAssessmentRepository,
            DecisionGoalRepository decisionGoalRepository
    ) {
        decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(loopId).forEach(decision -> {
            decisionEventRepository.deleteAll(
                    decisionEventRepository.findAllByDecisionIdOrderByOccurredAtAsc(decision.getId()));
            decisionAssessmentRepository.deleteAll(
                    decisionAssessmentRepository.findAllByDecisionId(decision.getId()));
            decisionGoalRepository.deleteAll(decisionGoalRepository.findAllByDecisionId(decision.getId()));
        });
        decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(loopId).stream()
                .filter(decision -> decision.getSupersedesDecisionId() != null)
                .forEach(decisionRepository::delete);
        decisionRepository.deleteAll(decisionRepository.findAllByCareLoopIdOrderByProposedAtDesc(loopId));

        scopeEventRepository.deleteAll(scopeEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loopId));
        statusEventRepository.deleteAll(statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loopId));
        careLoopRepository.findById(loopId).ifPresent(careLoopRepository::delete);
    }
}
