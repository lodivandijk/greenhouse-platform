package com.greenhouse.notification;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.CareLoopStatusEventRepository;
import com.greenhouse.careloop.decision.DecisionAssessmentRepository;
import com.greenhouse.careloop.decision.DecisionGoalRepository;
import com.greenhouse.careloop.decision.DecisionLifecycleEventRepository;
import com.greenhouse.careloop.decision.DecisionRepository;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;
import com.greenhouse.careloop.scope.LoopRecordScopeEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Policy decides whether a notification should EXIST. The property that matters
// most is idempotence: the sweep runs every five minutes over state that mostly
// does not change, and must not produce an email each time.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
@Import(FakeChannelConfiguration.class)
class NotificationPolicyTest {

    @Autowired private NotificationPolicyService policyService;
    @Autowired private com.greenhouse.careloop.CareLoopProjectionService projectionService;
    @Autowired private NotificationIntentRepository intentRepository;
    @Autowired private CareLoopRepository careLoopRepository;
    @Autowired private CareLoopStatusEventRepository statusEventRepository;
    @Autowired private LoopRecordScopeEventRepository scopeEventRepository;
    @Autowired private DecisionService decisionService;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private DecisionLifecycleEventRepository decisionEventRepository;
    @Autowired private DecisionAssessmentRepository decisionAssessmentRepository;
    @Autowired private DecisionGoalRepository decisionGoalRepository;

    private CareLoop loop;
    private final List<Long> createdIntentIds = new ArrayList<>();

    @BeforeEach
    void openLoop() {
        loop = NotificationTestSupport.openLoop(careLoopRepository, "CROP_SOIL_MOISTURE_LOW");
    }

    @AfterEach
    void cleanUp() {
        createdIntentIds.forEach(id -> intentRepository.findById(id).ifPresent(intentRepository::delete));
        createdIntentIds.clear();
        if (loop != null) {
            NotificationTestSupport.deleteLoop(loop.getId(), careLoopRepository, statusEventRepository,
                    scopeEventRepository, decisionRepository, decisionEventRepository,
                    decisionAssessmentRepository, decisionGoalRepository);
        }
    }

    // Only the intents this test caused; the dev database may hold real ones.
    private List<NotificationIntent> sweepForLoop() {
        List<NotificationIntent> created = policyService.findAndRecordCandidates();
        created.forEach(intent -> createdIntentIds.add(intent.getId()));
        return created.stream()
                .filter(intent -> loop.getId().equals(intent.getCareLoopId()))
                .toList();
    }

    @Test
    void anOpenLoopAwaitingReviewProducesExactlyOneIntent() {
        List<NotificationIntent> created = sweepForLoop();

        assertThat(created).hasSize(1);
        NotificationIntent intent = created.get(0);
        assertThat(intent.getIntentType()).isEqualTo(NotificationIntentType.ACTION_REQUIRED);
        assertThat(intent.getCareLoopId()).isEqualTo(loop.getId());
        assertThat(intent.getBriefingSnapshotId()).isNull();
        assertThat(intent.getPayload()).containsKey("nextRequiredAction");
    }

    @Test
    void repeatedSweepsOverUnchangedStateProduceNoDuplicate() {
        assertThat(sweepForLoop()).hasSize(1);

        // The five-minute sweep, three more times, with nothing having changed.
        assertThat(sweepForLoop()).isEmpty();
        assertThat(sweepForLoop()).isEmpty();
        assertThat(sweepForLoop()).isEmpty();

        assertThat(intentRepository.findAllByCareLoopIdOrderByCreatedAtDesc(loop.getId())).hasSize(1);
    }

    @Test
    void aGenuinelyNewLifecycleEventProducesANewIntent() {
        List<NotificationIntent> first = sweepForLoop();
        assertThat(first).hasSize(1);
        String firstFingerprint = (String) first.get(0).getPayload().get("actionableFingerprint");

        // A proposed decision changes what the human is being asked to do, so
        // it must reach them even though the loop id is unchanged.
        decisionService.propose(
                loop.getId(), CommandType.WATER_CROP, Map.of("cropId", 8, "quantity", 250, "unit", "ml"),
                "Soil index is low", List.of(), List.of(), "Soil index rises",
                OutcomeEvaluationMethod.SENSOR_BASED, 3600L, 7200L,
                "Soil index above 40", ActorType.HUMAN_DIRECT, "notification-policy-test",
                null, "notification-policy-test-" + System.nanoTime());

        List<NotificationIntent> second = sweepForLoop();
        assertThat(second).hasSize(1);
        assertThat((String) second.get(0).getPayload().get("actionableFingerprint"))
                .isNotEqualTo(firstFingerprint);
    }

    @Test
    void aClosedLoopProducesNoIntent() {
        loop.setClosedAt(Instant.now());
        careLoopRepository.save(loop);

        assertThat(sweepForLoop()).isEmpty();
    }

    @Test
    void theFingerprintIsIndependentOfTheClock() {
        List<NotificationIntent> created = sweepForLoop();
        String fingerprint = (String) created.get(0).getPayload().get("actionableFingerprint");

        // Recomputed later, from the same unchanged state.
        String recomputed = policyService
                .actionableState(loop, projectionService.projectStatus(loop.getId()))
                .fingerprint();

        assertThat(recomputed).isEqualTo(fingerprint);
    }
}
