package com.greenhouse.careloop;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.careloop.scope.LoopRecordScopeEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Covers the recovery defect: a care loop used to stay open forever, because
// closing was only ever attempted on the same tick that resolved an
// assessment - the one moment when no recovery time can possibly have elapsed.
//
// The essential behaviour is that a LATER tick, touching no assessments at all,
// still closes a loop whose condition has been clear long enough.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false"
})
class CareLoopRecoveryTest {

    private static final String GREENHOUSE_ID = "greenhouse-01";

    @Autowired private CareLoopCorrelationService correlationService;
    @Autowired private CareLoopRepository careLoopRepository;
    @Autowired private CareLoopAssessmentRepository careLoopAssessmentRepository;
    @Autowired private CareLoopStatusEventRepository statusEventRepository;
    @Autowired private LoopRecordScopeEventRepository scopeEventRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentMapper assessmentMapper;

    private final List<Long> createdAssessmentIds = new ArrayList<>();
    private final List<Long> createdLoopIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdLoopIds.forEach(loopId -> {
            careLoopAssessmentRepository.deleteAll(careLoopAssessmentRepository.findAllByCareLoopId(loopId));
            scopeEventRepository.deleteAll(scopeEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loopId));
            statusEventRepository.deleteAll(statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loopId));
            careLoopRepository.findById(loopId).ifPresent(careLoopRepository::delete);
        });
        createdAssessmentIds.forEach(id ->
                assessmentRepository.findById(id).ifPresent(assessmentRepository::delete));
        createdLoopIds.clear();
        createdAssessmentIds.clear();
    }

    private AssessmentEntity persistAssessment(
            String correlationKey, AssessmentStatus status, Instant firstDetectedAt, Instant resolvedAt
    ) {
        AssessmentEntity entity = new AssessmentEntity(
                null, correlationKey, GREENHOUSE_ID, "zone-main", null,
                AssessmentScopeType.DEVICE, "recovery-test-device",
                AssessmentCode.DEVICE_OFFLINE, AssessmentSeverity.WARNING, status,
                "recovery test", Map.of("k", "v"), "recovery-test-rule", 1,
                firstDetectedAt, firstDetectedAt, firstDetectedAt, resolvedAt,
                firstDetectedAt, firstDetectedAt
        );
        AssessmentEntity saved = assessmentRepository.save(entity);
        createdAssessmentIds.add(saved.getId());
        return saved;
    }

    private CareLoop openLoopFor(AssessmentEntity assessment, Instant openedAt) {
        // DEVICE_OFFLINE is immediately actionable, so one tick opens the loop.
        correlationService.correlate(
                new AssessmentChanges(List.of(assessmentMapper.toResponse(assessment)), List.of(), List.of()),
                openedAt
        );
        CareLoop loop = careLoopRepository
                .findByCorrelationKeyAndClosedAtIsNull(
                        AssessmentScopeType.DEVICE + ":" + assessment.getScopeId() + ":"
                                + AssessmentCode.DEVICE_OFFLINE.name())
                .orElseThrow();
        createdLoopIds.add(loop.getId());
        return loop;
    }

    private void resolve(AssessmentEntity assessment, Instant resolvedAt) {
        assessment.setStatus(AssessmentStatus.RESOLVED);
        assessment.setResolvedAt(resolvedAt);
        assessmentRepository.save(assessment);
    }

    private void tick(Instant at) {
        // A tick with no assessment changes at all - exactly the situation the
        // old implementation ignored.
        correlationService.correlate(new AssessmentChanges(List.of(), List.of(), List.of()), at);
    }

    @Test
    void loopStaysOpenImmediatelyAfterItsAssessmentResolves() {
        Instant t0 = Instant.now().minus(Duration.ofHours(3));
        AssessmentEntity assessment = persistAssessment(
                "recovery-a-" + System.nanoTime(), AssessmentStatus.ACTIVE, t0, null);
        CareLoop loop = openLoopFor(assessment, t0);

        Instant resolvedAt = t0.plus(Duration.ofMinutes(10));
        resolve(assessment, resolvedAt);
        tick(resolvedAt);

        assertThat(careLoopRepository.findById(loop.getId()).orElseThrow().getClosedAt())
                .as("no recovery time has elapsed yet")
                .isNull();
    }

    @Test
    void loopRemainsOpenBeforeTheRecoveryDurationElapses() {
        Instant t0 = Instant.now().minus(Duration.ofHours(3));
        AssessmentEntity assessment = persistAssessment(
                "recovery-b-" + System.nanoTime(), AssessmentStatus.ACTIVE, t0, null);
        CareLoop loop = openLoopFor(assessment, t0);

        Instant resolvedAt = t0.plus(Duration.ofMinutes(10));
        resolve(assessment, resolvedAt);

        // 29 minutes later - still inside the 30 minute default recovery window.
        tick(resolvedAt.plus(Duration.ofMinutes(29)));

        assertThat(careLoopRepository.findById(loop.getId()).orElseThrow().getClosedAt()).isNull();
    }

    @Test
    void aLaterTickClosesTheLoopOnceTheRecoveryDurationHasElapsed() {
        Instant t0 = Instant.now().minus(Duration.ofHours(3));
        AssessmentEntity assessment = persistAssessment(
                "recovery-c-" + System.nanoTime(), AssessmentStatus.ACTIVE, t0, null);
        CareLoop loop = openLoopFor(assessment, t0);

        Instant resolvedAt = t0.plus(Duration.ofMinutes(10));
        resolve(assessment, resolvedAt);
        tick(resolvedAt);

        // This is the case that previously never happened: a tick carrying no
        // assessment changes, long after the resolution.
        tick(resolvedAt.plus(Duration.ofMinutes(31)));

        CareLoop reloaded = careLoopRepository.findById(loop.getId()).orElseThrow();
        assertThat(reloaded.getClosedAt()).isNotNull();

        assertThat(statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()))
                .extracting(CareLoopStatusEvent::getStatus)
                .contains(CareLoopStatus.CLOSED);
    }

    @Test
    void anotherActiveLinkedAssessmentKeepsASharedLoopOpen() {
        Instant t0 = Instant.now().minus(Duration.ofHours(3));
        String sharedKey = "recovery-shared-" + System.nanoTime();

        AssessmentEntity first = persistAssessment(sharedKey + "-1", AssessmentStatus.ACTIVE, t0, null);
        CareLoop loop = openLoopFor(first, t0);

        // A second assessment joins the same loop and stays active.
        AssessmentEntity second = persistAssessment(sharedKey + "-2", AssessmentStatus.ACTIVE, t0, null);
        careLoopAssessmentRepository.save(new CareLoopAssessment(loop.getId(), second.getId(), t0));

        Instant resolvedAt = t0.plus(Duration.ofMinutes(10));
        resolve(first, resolvedAt);

        tick(resolvedAt.plus(Duration.ofMinutes(45)));

        assertThat(careLoopRepository.findById(loop.getId()).orElseThrow().getClosedAt())
                .as("one crop recovering must not close a loop others still need")
                .isNull();

        // Once the second also resolves and its own recovery window passes, the
        // loop closes.
        Instant secondResolvedAt = resolvedAt.plus(Duration.ofMinutes(50));
        resolve(second, secondResolvedAt);
        tick(secondResolvedAt.plus(Duration.ofMinutes(31)));

        assertThat(careLoopRepository.findById(loop.getId()).orElseThrow().getClosedAt()).isNotNull();
    }

    @Test
    void closingIsRecordedOnceAndDoesNotRepeatOnLaterTicks() {
        Instant t0 = Instant.now().minus(Duration.ofHours(3));
        AssessmentEntity assessment = persistAssessment(
                "recovery-d-" + System.nanoTime(), AssessmentStatus.ACTIVE, t0, null);
        CareLoop loop = openLoopFor(assessment, t0);

        Instant resolvedAt = t0.plus(Duration.ofMinutes(10));
        resolve(assessment, resolvedAt);

        tick(resolvedAt.plus(Duration.ofMinutes(31)));
        tick(resolvedAt.plus(Duration.ofMinutes(32)));
        tick(resolvedAt.plus(Duration.ofMinutes(45)));

        long closedEvents = statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()).stream()
                .filter(event -> event.getStatus() == CareLoopStatus.CLOSED)
                .count();
        assertThat(closedEvents).isEqualTo(1);
    }
}
