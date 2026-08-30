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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Covers the two scheduling defects fixed alongside the notification work:
// a briefing must not be generated before its configured time, and there must
// be exactly one setting deciding when that time is.
//
// Not @Transactional - generateIfDue commits, and the point is to observe what
// actually persists across calls. Snapshots are cleaned up explicitly.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false"
})
class DailyBriefingSchedulingTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Autowired
    private DailyBriefingService briefingService;

    @Autowired
    private DailyBriefingSnapshotRepository snapshotRepository;

    // The service reads the clock through this bean, so overriding it lets the
    // test stand at a chosen moment of the greenhouse day.
    @MockitoBean
    private Clock clock;

    private void standAt(String isoInstant) {
        Instant at = Instant.parse(isoInstant);
        org.mockito.Mockito.when(clock.instant()).thenReturn(at);
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
        deleteSnapshotsFor(LocalDate.of(2026, 3, 14));
        deleteSnapshotsFor(LocalDate.of(2026, 3, 29));
        deleteSnapshotsFor(LocalDate.of(2026, 10, 25));
    }

    @Test
    void startingBeforeTheConfiguredTimeGeneratesNothing() {
        // 02:00 London on a normal day - the 06:00 briefing is not late, it
        // simply has not come round yet. This is the case that used to produce
        // a midnight "daily briefing" on every restart.
        standAt("2026-03-14T02:00:00Z");

        assertThat(briefingService.generateIfDue(true)).isEmpty();
        assertThat(snapshotRepository.existsByGreenhouseDay(LocalDate.of(2026, 3, 14))).isFalse();
    }

    @Test
    void startingAfterTheConfiguredTimeRecoversExactlyOneBriefing() {
        standAt("2026-03-14T09:00:00Z");

        Optional<DailyBriefingSnapshot> first = briefingService.generateIfDue(true);
        assertThat(first).isPresent();
        assertThat(first.get().getMissedRunRecovery()).isTrue();
        assertThat(first.get().getGreenhouseDay()).isEqualTo(LocalDate.of(2026, 3, 14));

        // A second startup event, or the next scheduler tick, must not duplicate.
        assertThat(briefingService.generateIfDue(true)).isEmpty();
        assertThat(snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(LocalDate.of(2026, 3, 14)))
                .hasSize(1);
    }

    @Test
    void repeatedTicksAcrossTheDayGenerateOnlyOneBriefing() {
        standAt("2026-03-14T05:59:00Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();

        standAt("2026-03-14T06:00:30Z");
        assertThat(briefingService.generateIfDue(false)).isPresent();

        standAt("2026-03-14T06:01:30Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();
        standAt("2026-03-14T18:00:00Z");
        assertThat(briefingService.generateIfDue(false)).isEmpty();

        assertThat(snapshotRepository.findAllByGreenhouseDayOrderByGeneratedAtAsc(LocalDate.of(2026, 3, 14)))
                .hasSize(1);
    }

    @Test
    void springForwardIsHandledByZoneIdNotAFixedOffset() {
        // Britain moves to BST on 2026-03-29, so 06:00 local is 05:00 UTC.
        // A fixed UTC offset would generate an hour late.
        standAt("2026-03-29T05:30:00Z");
        assertThat(briefingService.generateIfDue(false))
                .as("05:30 UTC is 06:30 BST - the briefing is due")
                .isPresent();
    }

    @Test
    void autumnBackIsHandledByZoneIdNotAFixedOffset() {
        // Back to GMT on 2026-10-25, so 06:00 local is 06:00 UTC. At 05:30 UTC
        // it is only 05:30 locally and nothing should be generated.
        standAt("2026-10-25T05:30:00Z");
        assertThat(briefingService.generateIfDue(false))
                .as("05:30 UTC is 05:30 GMT - not due yet")
                .isEmpty();

        standAt("2026-10-25T06:30:00Z");
        assertThat(briefingService.generateIfDue(false)).isPresent();
    }

    @Test
    void regenerationCreatesANewVersionRatherThanOverwriting() {
        standAt("2026-03-14T09:00:00Z");
        DailyBriefingSnapshot original = briefingService.generateIfDue(false).orElseThrow();

        DailyBriefingSnapshot regenerated = briefingService.regenerate(LocalDate.of(2026, 3, 14));

        assertThat(regenerated.getId()).isNotEqualTo(original.getId());
        assertThat(regenerated.getSupersedesSnapshotId()).isEqualTo(original.getId());
        // The original survives intact - that is what makes it a historical record.
        assertThat(snapshotRepository.findById(original.getId())).isPresent();
    }
}
