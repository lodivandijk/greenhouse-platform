package com.greenhouse.briefing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// A briefing must not be generated before its time, there must be exactly one
// setting deciding when that time is, and - since there are now two editions a
// day - a late start must not fire both at once (ADR-023 §13.2, ADR-033).
//
// Not @Transactional: generateIfDue commits, and the point is to observe what
// actually persists across calls. Snapshots are cleaned up explicitly.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class DailyBriefingSchedulingTest {

    private static final LocalDate MAR_13 = LocalDate.of(2026, 3, 13);
    private static final LocalDate MAR_14 = LocalDate.of(2026, 3, 14);

    @Autowired private DailyBriefingService briefingService;
    @Autowired private DailyBriefingSnapshotRepository snapshotRepository;
    @Autowired private DailyBriefingProperties properties;

    // The service reads the clock through this bean, so overriding it lets the
    // test stand at a chosen moment of the greenhouse day.
    @MockitoBean private Clock clock;

    private void standAt(String isoInstant) {
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse(isoInstant));
        org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    private void deleteSnapshotsFor(LocalDate day) {
        List<DailyBriefingSnapshot> existing =
                snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(day);
        // A regenerated snapshot references the one it supersedes.
        existing.stream().filter(s -> s.getSupersedesSnapshotId() != null)
                .forEach(snapshotRepository::delete);
        snapshotRepository.deleteAll(snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(day));
    }

    @AfterEach
    void cleanUp() {
        List.of(MAR_13, MAR_14,
                        LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29),
                        LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25))
                .forEach(this::deleteSnapshotsFor);
    }

    private boolean exists(LocalDate day, BriefingEdition edition) {
        return snapshotRepository.existsByGreenhouseDayAndEdition(day, edition);
    }

    // --- not before its time ---------------------------------------------

    @Test
    void theMorningEditionIsNotGeneratedBeforeItsTime() {
        // 02:00 GMT. The 06:00 briefing is not late, it simply has not come
        // round yet. This is the case that used to produce a midnight
        // "daily briefing" on every restart.
        standAt("2026-03-14T02:00:00Z");

        briefingService.generateIfDue(true);

        assertThat(exists(MAR_14, BriefingEdition.MORNING)).isFalse();
    }

    @Test
    void theMorningEditionIsRecoveredAfterItsTime() {
        standAt("2026-03-14T09:00:00Z");

        List<DailyBriefingSnapshot> first = briefingService.generateIfDue(true);

        assertThat(first).hasSize(1);
        assertThat(first.get(0).getEdition()).isEqualTo(BriefingEdition.MORNING);
        assertThat(first.get(0).getGreenhouseDay()).isEqualTo(MAR_14);
        assertThat(first.get(0).getMissedRunRecovery()).isTrue();

        // A second startup event, or the next tick, must not duplicate.
        assertThat(briefingService.generateIfDue(true)).isEmpty();
        assertThat(snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(MAR_14)).hasSize(1);
    }

    // --- the evening edition ---------------------------------------------

    @Test
    void theEveningEditionIsGeneratedAtItsOwnTime() {
        standAt("2026-03-14T09:00:00Z");
        briefingService.generateIfDue(false);

        standAt("2026-03-14T19:30:00Z");
        List<DailyBriefingSnapshot> evening = briefingService.generateIfDue(false);

        assertThat(evening).hasSize(1);
        assertThat(evening.get(0).getEdition()).isEqualTo(BriefingEdition.EVENING);
        assertThat(exists(MAR_14, BriefingEdition.MORNING)).isTrue();
    }

    // THE property this design exists for: starting the application in the
    // evening must not send a twelve-hour-stale morning briefing alongside the
    // current one.
    @Test
    void aLateStartGeneratesOnlyTheCurrentEdition() {
        standAt("2026-03-14T20:00:00Z");

        List<DailyBriefingSnapshot> generated = briefingService.generateIfDue(true);

        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getEdition()).isEqualTo(BriefingEdition.EVENING);
        assertThat(exists(MAR_14, BriefingEdition.MORNING))
                .as("a morning briefing generated at 20:00 is not recovered, it is wrong")
                .isFalse();
    }

    // Between 19:00 and the next 06:00, yesterday's evening edition is still
    // the current one, so an overnight restart recovers it.
    @Test
    void yesterdaysEveningEditionIsRecoveredOvernight() {
        standAt("2026-03-14T02:00:00Z");

        List<DailyBriefingSnapshot> generated = briefingService.generateIfDue(true);

        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getEdition()).isEqualTo(BriefingEdition.EVENING);
        assertThat(generated.get(0).getGreenhouseDay()).isEqualTo(MAR_13);
    }

    @Test
    void repeatedTicksAcrossADayGenerateEachEditionExactlyOnce() {
        standAt("2026-03-14T05:59:00Z");
        briefingService.generateIfDue(false);

        standAt("2026-03-14T06:00:30Z");
        assertThat(briefingService.generateIfDue(false)).hasSize(1);
        standAt("2026-03-14T06:01:30Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();
        standAt("2026-03-14T12:00:00Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();

        standAt("2026-03-14T19:00:30Z");
        assertThat(briefingService.generateIfDue(false)).hasSize(1);
        standAt("2026-03-14T21:00:00Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();

        assertThat(snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(MAR_14)).hasSize(2);
    }

    // --- windows ----------------------------------------------------------

    // Each edition reports on what changed since the previous one, which is
    // what lets the evening briefing answer "what happened while I was out".
    @Test
    void eachEditionReportsSinceThePreviousEdition() {
        standAt("2026-03-14T09:00:00Z");
        DailyBriefingSnapshot morning = briefingService.generateIfDue(false).get(0);

        standAt("2026-03-14T19:30:00Z");
        DailyBriefingSnapshot evening = briefingService.generateIfDue(false).get(0);

        assertThat(morning.getWindowStart())
                .isEqualTo(properties.scheduledFor(BriefingEdition.EVENING, MAR_13).toInstant());
        assertThat(evening.getWindowStart())
                .isEqualTo(properties.scheduledFor(BriefingEdition.MORNING, MAR_14).toInstant());
    }

    // --- daylight saving --------------------------------------------------

    @Test
    void springForwardIsHandledByZoneIdNotAFixedOffset() {
        // Britain moves to BST on 2026-03-29, so 06:00 local is 05:00 UTC.
        // A fixed UTC offset would generate an hour late.
        standAt("2026-03-29T05:30:00Z");

        briefingService.generateIfDue(false);

        assertThat(exists(LocalDate.of(2026, 3, 29), BriefingEdition.MORNING))
                .as("05:30 UTC is 06:30 BST - the morning briefing is due")
                .isTrue();
    }

    @Test
    void autumnBackIsHandledByZoneIdNotAFixedOffset() {
        // Back to GMT on 2026-10-25, so 06:00 local is 06:00 UTC.
        standAt("2026-10-25T05:30:00Z");
        briefingService.generateIfDue(false);
        assertThat(exists(LocalDate.of(2026, 10, 25), BriefingEdition.MORNING))
                .as("05:30 UTC is 05:30 GMT - not due yet")
                .isFalse();

        standAt("2026-10-25T06:30:00Z");
        briefingService.generateIfDue(false);
        assertThat(exists(LocalDate.of(2026, 10, 25), BriefingEdition.MORNING)).isTrue();
    }

    // --- regeneration -----------------------------------------------------

    @Test
    void regenerationCreatesANewVersionRatherThanOverwriting() {
        standAt("2026-03-14T09:00:00Z");
        DailyBriefingSnapshot original = briefingService.generateIfDue(false).get(0);

        DailyBriefingSnapshot regenerated =
                briefingService.regenerate(MAR_14, BriefingEdition.MORNING);

        assertThat(regenerated.getId()).isNotEqualTo(original.getId());
        assertThat(regenerated.getEdition()).isEqualTo(BriefingEdition.MORNING);
        assertThat(regenerated.getSupersedesSnapshotId()).isEqualTo(original.getId());
        // The original survives intact - that is what makes it a record.
        assertThat(snapshotRepository.findById(original.getId())).isPresent();
    }
}
