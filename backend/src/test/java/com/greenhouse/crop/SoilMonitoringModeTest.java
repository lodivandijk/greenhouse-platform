package com.greenhouse.crop;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentLifecycleEventRepository;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.assessment.reconciliation.AssessmentReconciler;
import com.greenhouse.assessment.rule.CropSoilMoistureAssessmentRule;
import com.greenhouse.briefing.DailyBriefingService;
import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopAssessmentRepository;
import com.greenhouse.careloop.CareLoopCorrelationService;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.CareLoopStatusEventRepository;
import com.greenhouse.careloop.scope.LoopRecordScopeEventRepository;
import com.greenhouse.common.DomainValidationException;
import com.greenhouse.twin.TwinService;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The end-to-end consequence of ADR-024, against a real database: a crop that
// deliberately has no probe must stop raising a sensor fault, its existing
// assessment must resolve, and the care loop holding that fault open must
// actually close and stay closed.
//
// This is driven through the real rule, reconciler and correlation service with
// explicit timestamps rather than a mocked clock, because the thing being
// tested is what happens across several evaluation ticks.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class SoilMonitoringModeTest {

    @Autowired private CropRepository cropRepository;
    @Autowired private CropService cropService;
    @Autowired private CropMonitoringProfileService profileService;
    @Autowired private CropMonitoringProfileRepository profileRepository;
    @Autowired private CropSoilMoistureAssessmentRule soilRule;
    @Autowired private AssessmentReconciler reconciler;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentLifecycleEventRepository lifecycleEventRepository;
    @Autowired private CareLoopCorrelationService correlationService;
    @Autowired private CareLoopRepository careLoopRepository;
    @Autowired private CareLoopAssessmentRepository careLoopAssessmentRepository;
    @Autowired private CareLoopStatusEventRepository statusEventRepository;
    @Autowired private LoopRecordScopeEventRepository scopeEventRepository;
    @Autowired private DailyBriefingService briefingService;
    @Autowired private TwinService twinService;
    @Autowired private TwinProperties twinProperties;

    private Crop crop;

    @BeforeEach
    void createCropWithNoProbe() {
        // Through the domain service, so the crop is created exactly as a real
        // one is - the entity has audit columns the service is responsible for.
        Long cropId = cropService.createCrop(
                "Tarragon", null, "planter-test", Instant.now(), "soil monitoring mode test").id();
        crop = cropRepository.findById(cropId).orElseThrow();

        profileService.createVersion(
                crop.getId(), 15.0, 24.0, 3600L, 1800L,
                SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0,
                SoilMonitoringMode.SENSOR, "test", "Initial sensor-monitored profile.");
    }

    @AfterEach
    void cleanUp() {
        careLoopRepository.findAll().stream()
                .filter(loop -> loop.getCorrelationKey().contains(":" + crop.getId() + ":")
                        || loop.getPrimarySubjectId().equals(String.valueOf(crop.getId())))
                .forEach(loop -> {
                    careLoopAssessmentRepository.deleteAll(
                            careLoopAssessmentRepository.findAllByCareLoopId(loop.getId()));
                    scopeEventRepository.deleteAll(
                            scopeEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()));
                    statusEventRepository.deleteAll(
                            statusEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()));
                    careLoopRepository.delete(loop);
                });

        assessmentRepository.findAll().stream()
                .filter(assessment -> crop.getId().equals(assessment.getCropId()))
                .forEach(assessment -> {
                    lifecycleEventRepository.deleteAll(
                            lifecycleEventRepository.findAllByAssessmentIdOrderByOccurredAtAsc(assessment.getId()));
                    assessmentRepository.delete(assessment);
                });

        // Superseding rows point at the ones they replace, so newest first.
        profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId())
                .forEach(profileRepository::delete);

        cropRepository.findById(crop.getId()).ifPresent(cropRepository::delete);
    }

    private AssessmentChanges evaluateAt(Instant at) {
        GreenhouseTwin twin = twinService.getCurrentTwin();
        List<AssessmentFinding> findings = soilRule.evaluate(twin, at);
        AssessmentChanges changes = reconciler.reconcile(twinProperties.greenhouseId(), findings, at);
        correlationService.correlate(changes, at);
        return changes;
    }

    // Newest first: a recurrence is a NEW assessment row, not a revival of the
    // resolved one, so "the current assessment" is the most recent.
    private Optional<AssessmentEntity> soilAssessment() {
        return soilAssessments().stream().findFirst();
    }

    private List<AssessmentEntity> soilAssessments() {
        return assessmentRepository.findAll().stream()
                .filter(assessment -> crop.getId().equals(assessment.getCropId()))
                .filter(assessment -> assessment.getCode() == AssessmentCode.CROP_SENSOR_NOT_ASSIGNED)
                .sorted(java.util.Comparator.comparing(AssessmentEntity::getId).reversed())
                .toList();
    }

    private Optional<CareLoop> soilLoop() {
        return careLoopRepository.findAll().stream()
                .filter(loop -> String.valueOf(crop.getId()).equals(loop.getPrimarySubjectId()))
                .filter(loop -> "CROP_SENSOR_NOT_ASSIGNED".equals(loop.getConditionType()))
                .findFirst();
    }

    @Test
    void changingTheModeCreatesANewVersionAndPreservesTheOldOne() {
        CropMonitoringProfile updated = profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "No probe wired; tended by hand.", "test-actor");

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getSoilMonitoringMode()).isEqualTo(SoilMonitoringMode.MANUAL);
        assertThat(updated.getEnabled()).isTrue();
        assertThat(updated.getSourceNotes()).isEqualTo("No probe wired; tended by hand.");
        assertThat(updated.getCreatedBy()).isEqualTo("test-actor");

        List<CropMonitoringProfile> history =
                profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId());
        assertThat(history).hasSize(2);

        CropMonitoringProfile original = history.get(1);
        assertThat(original.getVersion()).isEqualTo(1);
        assertThat(original.getEnabled()).isFalse();
        assertThat(original.getSoilMonitoringMode()).isEqualTo(SoilMonitoringMode.SENSOR);
        assertThat(updated.getSupersedesProfileId()).isEqualTo(original.getId());

        // Everything other than the mode is carried forward, so opting out of
        // sensor assessment cannot silently reset the crop's thresholds.
        assertThat(updated.getSoilDryThresholdIndex()).isEqualTo(original.getSoilDryThresholdIndex());
        assertThat(updated.getSoilWetThresholdIndex()).isEqualTo(original.getSoilWetThresholdIndex());
        assertThat(updated.getPreferredTemperatureMaxCelsius())
                .isEqualTo(original.getPreferredTemperatureMaxCelsius());
    }

    @Test
    void aRationaleIsRequiredBecauseTheReasonMustBeRecordedNotInferred() {
        assertThatThrownBy(() -> profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "  ", "test-actor"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("rationale");
    }

    @Test
    void settingTheModeItAlreadyHasCreatesNoVersion() {
        CropMonitoringProfile unchanged = profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.SENSOR, "Already sensor-monitored.", "test-actor");

        assertThat(unchanged.getVersion()).isEqualTo(1);
        assertThat(profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId())).hasSize(1);
    }

    @Test
    void theAssessmentResolvesAndItsLoopClosesAndDoesNotReopen() {
        Instant t0 = Instant.now().minus(Duration.ofHours(4));

        // The condition as it stands today: a sensor-monitored crop with no
        // probe. Sensor-quality conditions are actionable immediately, so the
        // loop opens on this first tick.
        evaluateAt(t0);
        assertThat(soilAssessment()).get()
                .extracting(AssessmentEntity::getStatus).isEqualTo(AssessmentStatus.ACTIVE);
        assertThat(soilLoop()).get().extracting(CareLoop::getClosedAt).isNull();

        Long loopId = soilLoop().orElseThrow().getId();

        profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL,
                "Deliberately monitored by hand; no probe is wired.", "test-actor");

        // The rule now emits nothing for this crop, so the reconciler resolves
        // the assessment.
        Instant t1 = t0.plus(Duration.ofMinutes(1));
        AssessmentChanges changes = evaluateAt(t1);
        assertThat(changes.resolved())
                .anyMatch(assessment -> assessment.code() == AssessmentCode.CROP_SENSOR_NOT_ASSIGNED);
        assertThat(soilAssessment()).get()
                .extracting(AssessmentEntity::getStatus).isEqualTo(AssessmentStatus.RESOLVED);

        // Resolved, but the loop must not close until the recovery duration has
        // elapsed - closing instantly would defeat the point of the timer.
        assertThat(careLoopRepository.findById(loopId)).get()
                .extracting(CareLoop::getClosedAt).isNull();

        // A LATER tick that touches no assessments at all still closes it. This
        // is the state-driven recovery path; the old delta-driven one could
        // never reach this point.
        Instant t2 = t1.plus(Duration.ofHours(1));
        evaluateAt(t2);
        assertThat(careLoopRepository.findById(loopId)).get()
                .extracting(CareLoop::getClosedAt).isNotNull();

        // And it stays closed: repeated evaluation must not reopen it while the
        // crop remains MANUAL. This is what turns off the reminder emails.
        evaluateAt(t2.plus(Duration.ofMinutes(5)));
        evaluateAt(t2.plus(Duration.ofMinutes(10)));

        assertThat(careLoopRepository.findAll().stream()
                .filter(loop -> String.valueOf(crop.getId()).equals(loop.getPrimarySubjectId()))
                .filter(loop -> loop.getClosedAt() == null))
                .isEmpty();
        assertThat(soilAssessment()).get()
                .extracting(AssessmentEntity::getStatus).isEqualTo(AssessmentStatus.RESOLVED);
    }

    @Test
    void switchingBackToSensorRaisesTheConditionAgain() {
        Instant t0 = Instant.now().minus(Duration.ofHours(4));
        evaluateAt(t0);
        profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "No probe wired.", "test-actor");
        evaluateAt(t0.plus(Duration.ofMinutes(1)));
        assertThat(soilAssessment()).get()
                .extracting(AssessmentEntity::getStatus).isEqualTo(AssessmentStatus.RESOLVED);

        // A probe is wired later, so the crop goes back to being sensor-assessed.
        profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.SENSOR, "Probe soil-06 wired.", "test-actor");
        evaluateAt(t0.plus(Duration.ofMinutes(2)));

        // The condition recurs as a NEW assessment; the resolved one is history
        // and is never revived, so the record of when the crop was manual and
        // when it was not stays intact.
        List<AssessmentEntity> all = soilAssessments();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getStatus()).isEqualTo(AssessmentStatus.ACTIVE);
        assertThat(all.get(1).getStatus()).isEqualTo(AssessmentStatus.RESOLVED);

        assertThat(profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId())).hasSize(3);
    }

    @Test
    void theBriefingReportsManualMonitoringAsUnknownRatherThanAsAGap() {
        profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "No probe wired.", "test-actor");

        Map<String, Object> briefing = briefingService.buildCurrentBriefing();

        Map<String, Object> cropEntry = cropEntryFor(briefing, crop.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> soil = (Map<String, Object>) cropEntry.get("soil");

        assertThat(soil).containsEntry("soilMonitoringMode", "MANUAL");
        // Suppressing the assessment must never read as "the soil is fine".
        assertThat(soil).containsEntry("status", "UNKNOWN");
        assertThat(soil).containsEntry("reason", "MANUAL_MONITORING");
        assertThat(String.valueOf(soil.get("note"))).contains("looking at it");
        assertThat(soil).doesNotContainKey("moistureIndex");

        // A chosen absence is not a failed measurement.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) briefing.get("dataQualityGaps");
        assertThat(gaps)
                .noneMatch(gap -> "NO_SENSOR_ASSIGNED".equals(gap.get("kind"))
                        && crop.getId().equals(gap.get("cropId")));
    }

    @Test
    void aSensorMonitoredCropWithNoProbeIsStillReportedAsAGap() {
        Map<String, Object> briefing = briefingService.buildCurrentBriefing();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) briefing.get("dataQualityGaps");
        assertThat(gaps)
                .anyMatch(gap -> "NO_SENSOR_ASSIGNED".equals(gap.get("kind"))
                        && crop.getId().equals(gap.get("cropId")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cropEntryFor(Map<String, Object> briefing, Long cropId) {
        List<Map<String, Object>> crops = (List<Map<String, Object>>) briefing.get("crops");
        return crops.stream()
                .filter(entry -> cropId.equals(entry.get("cropId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("crop " + cropId + " missing from the briefing"));
    }
}
