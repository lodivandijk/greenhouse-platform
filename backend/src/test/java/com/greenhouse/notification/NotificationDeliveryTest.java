package com.greenhouse.notification;

import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.CareLoopStatusEventRepository;
import com.greenhouse.careloop.decision.DecisionAssessmentRepository;
import com.greenhouse.careloop.decision.DecisionGoalRepository;
import com.greenhouse.careloop.decision.DecisionLifecycleEventRepository;
import com.greenhouse.careloop.decision.DecisionRepository;
import com.greenhouse.careloop.scope.LoopRecordScopeEventRepository;
import com.greenhouse.notification.delivery.DeliveryRequest;
import com.greenhouse.notification.delivery.DeliveryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Exercises the delivery state machine through a fake channel. No SMTP, no
// network, no real email - the port exists precisely so this is possible.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
@Import(FakeChannelConfiguration.class)
class NotificationDeliveryTest {


    @Autowired private NotificationPolicyService policyService;
    @Autowired private NotificationDeliveryService deliveryService;
    @Autowired private NotificationIntentRepository intentRepository;
    @Autowired private NotificationDeliveryEventRepository deliveryEventRepository;
    @Autowired private RecordingDeliveryPort port;
    @Autowired private CareLoopRepository careLoopRepository;
    @Autowired private CareLoopStatusEventRepository statusEventRepository;
    @Autowired private LoopRecordScopeEventRepository scopeEventRepository;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private DecisionLifecycleEventRepository decisionEventRepository;
    @Autowired private DecisionAssessmentRepository decisionAssessmentRepository;
    @Autowired private DecisionGoalRepository decisionGoalRepository;

    private CareLoop loop;
    private final List<Long> createdIntentIds = new ArrayList<>();
    private final List<Long> extraLoopIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        port.reset();
        loop = NotificationTestSupport.openLoop(careLoopRepository, "CROP_SOIL_MOISTURE_LOW");
    }

    @AfterEach
    void cleanUp() {
        createdIntentIds.forEach(id -> {
            deliveryEventRepository.deleteAll(
                    deliveryEventRepository.findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(id));
            intentRepository.findById(id).ifPresent(intentRepository::delete);
        });
        createdIntentIds.clear();
        // Only now the intents are gone can the loops they point at be deleted.
        extraLoopIds.forEach(id -> NotificationTestSupport.deleteLoop(id, careLoopRepository,
                statusEventRepository, scopeEventRepository, decisionRepository, decisionEventRepository,
                decisionAssessmentRepository, decisionGoalRepository));
        extraLoopIds.clear();
        if (loop != null) {
            NotificationTestSupport.deleteLoop(loop.getId(), careLoopRepository, statusEventRepository,
                    scopeEventRepository, decisionRepository, decisionEventRepository,
                    decisionAssessmentRepository, decisionGoalRepository);
        }
    }

    private NotificationIntent createIntent() {
        List<NotificationIntent> created = policyService.findAndRecordCandidates();
        created.forEach(intent -> createdIntentIds.add(intent.getId()));
        return created.stream()
                .filter(intent -> loop.getId().equals(intent.getCareLoopId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("policy created no intent for the test loop"));
    }

    // The sweep is global by design - it delivers every pending intent, not
    // just this test's. Assertions are therefore scoped to the one intent under
    // test rather than to everything the port happened to see.
    private List<DeliveryRequest> requestsFor(NotificationIntent intent) {
        return port.requests().stream()
                .filter(request -> intent.getId().equals(request.notificationIntentId()))
                .toList();
    }

    private List<NotificationDeliveryEvent> eventsFor(NotificationIntent intent) {
        return deliveryEventRepository
                .findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(intent.getId());
    }

    @Test
    void aSuccessfulDeliveryRecordsAttemptedThenSent() {
        NotificationIntent intent = createIntent();

        deliveryService.deliverPending();

        assertThat(eventsFor(intent))
                .extracting(NotificationDeliveryEvent::getEventType)
                .containsExactly(
                        NotificationDeliveryEventType.ATTEMPTED,
                        NotificationDeliveryEventType.SENT);
        assertThat(requestsFor(intent)).hasSize(1);
        assertThat(requestsFor(intent).get(0).subject()).isNotBlank();
        assertThat(requestsFor(intent).get(0).plainTextBody()).isNotBlank();
    }

    @Test
    void aSentIntentIsNotSentAgainOnTheNextSweep() {
        NotificationIntent intent = createIntent();
        deliveryService.deliverPending();
        deliveryService.deliverPending();
        deliveryService.deliverPending();

        assertThat(requestsFor(intent)).hasSize(1);
        assertThat(eventsFor(intent)).hasSize(2);
    }

    @Test
    void aRetryableFailureSchedulesTheNextAttempt() {
        port.respondWith(request -> DeliveryResult.retryable("SMTP_SEND", "connection reset"));
        NotificationIntent intent = createIntent();

        deliveryService.deliverPending();

        List<NotificationDeliveryEvent> events = eventsFor(intent);
        assertThat(events).extracting(NotificationDeliveryEvent::getEventType)
                .containsExactly(
                        NotificationDeliveryEventType.ATTEMPTED,
                        NotificationDeliveryEventType.FAILED);

        NotificationDeliveryEvent failed = events.get(1);
        assertThat(failed.getErrorCode()).isEqualTo("SMTP_SEND");
        assertThat(failed.getNextAttemptAt())
                .isNotNull()
                .isAfter(failed.getOccurredAt());

        // The backoff is real: an immediate sweep must not retry.
        deliveryService.deliverPending();
        assertThat(requestsFor(intent)).hasSize(1);
    }

    @Test
    void aPermanentFailureIsAbandonedRatherThanRetriedForever() {
        port.respondWith(request -> DeliveryResult.permanent("SMTP_AUTH", "authentication rejected"));
        NotificationIntent intent = createIntent();

        deliveryService.deliverPending();
        deliveryService.deliverPending();

        assertThat(eventsFor(intent)).extracting(NotificationDeliveryEvent::getEventType)
                .containsExactly(
                        NotificationDeliveryEventType.ATTEMPTED,
                        NotificationDeliveryEventType.ABANDONED);
        // Bad credentials do not fix themselves; hammering the provider would
        // only get the account locked.
        assertThat(requestsFor(intent)).hasSize(1);
    }

    @Test
    void anIntentWhoseLoopClosedBeforeDeliveryIsSuppressedAndNotSent() {
        NotificationIntent intent = createIntent();

        // The human dealt with it in the gap between policy and delivery.
        loop.setClosedAt(Instant.now());
        careLoopRepository.save(loop);

        deliveryService.deliverPending();

        assertThat(eventsFor(intent)).extracting(NotificationDeliveryEvent::getEventType)
                .containsExactly(NotificationDeliveryEventType.SUPPRESSED);
        assertThat(requestsFor(intent)).isEmpty();
    }

    @Test
    void oneFailingIntentDoesNotBlockTheOthers() {
        NotificationIntent first = createIntent();

        CareLoop second = NotificationTestSupport.openLoop(careLoopRepository, "CROP_TEMPERATURE_HIGH");
        extraLoopIds.add(second.getId());
        List<NotificationIntent> more = policyService.findAndRecordCandidates();
        more.forEach(intent -> createdIntentIds.add(intent.getId()));
        NotificationIntent secondIntent = more.stream()
                .filter(intent -> second.getId().equals(intent.getCareLoopId()))
                .findFirst()
                .orElseThrow();

        port.respondWith(request -> {
            if (request.notificationIntentId().equals(first.getId())) {
                throw new IllegalStateException("adapter blew up");
            }
            return DeliveryResult.success("fake-ok");
        });

        deliveryService.deliverPending();

        assertThat(eventsFor(secondIntent)).extracting(NotificationDeliveryEvent::getEventType)
                .contains(NotificationDeliveryEventType.SENT);
        // The thrown exception is classified, not propagated.
        assertThat(eventsFor(first)).extracting(NotificationDeliveryEvent::getEventType)
                .contains(NotificationDeliveryEventType.FAILED);
    }

    @Test
    void theMessageIdIsStableAcrossAttemptsOfTheSameIntent() {
        port.respondWith(request -> DeliveryResult.retryable("SMTP_SEND", "timeout"));
        NotificationIntent intent = createIntent();

        deliveryService.deliverPending();
        String first = requestsFor(intent).get(0).deterministicMessageId();

        assertThat(first).contains(String.valueOf(intent.getId()));
        assertThat(first).startsWith("<greenhouse-notification-").endsWith(">");
    }
}
