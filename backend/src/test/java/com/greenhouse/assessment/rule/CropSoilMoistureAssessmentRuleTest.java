package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropMonitoringProfile;
import com.greenhouse.crop.CropMonitoringProfileService;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropStatus;
import com.greenhouse.crop.SoilMoistureStrategy;
import com.greenhouse.crop.SoilMonitoringMode;
import com.greenhouse.observation.assignment.CropSensorAssignment;
import com.greenhouse.observation.assignment.CropSensorAssignmentService;
import com.greenhouse.observation.calibration.MoistureIndex;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.SoilMoistureTwin;
import com.greenhouse.twin.status.FreshnessStatus;
import com.greenhouse.twin.status.TwinStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropSoilMoistureAssessmentRuleTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static final String GREENHOUSE_ID = "greenhouse-01";

    @Mock
    private CropRepository cropRepository;

    @Mock
    private CropMonitoringProfileService profileService;

    @Mock
    private CropSensorAssignmentService assignmentService;

    @Mock
    private SensorCalibrationService calibrationService;

    private CropSoilMoistureAssessmentRule rule;

    @BeforeEach
    void setUp() {
        TwinProperties twinProperties = new TwinProperties(
                GREENHOUSE_ID, "Home Greenhouse", Duration.ofMinutes(2), Duration.ofMinutes(5),
                new TwinProperties.EnvironmentalLimits(5.0, 35.0, 25.0, 90.0),
                List.of(new com.greenhouse.twin.config.ZoneProperties("zone-main", "Main", List.of("device-1")))
        );
        rule = new CropSoilMoistureAssessmentRule(
                cropRepository, profileService, assignmentService, calibrationService,
                twinProperties, new AssessmentCorrelationKeyFactory()
        );
    }

    private static Crop crop(Long id, String species) {
        Crop crop = new Crop();
        crop.setId(id);
        crop.setSpecies(species);
        crop.setStatus(CropStatus.PRODUCTIVE);
        return crop;
    }

    private static CropMonitoringProfile profile(Long cropId, SoilMoistureStrategy strategy, Double dry, Double wet) {
        CropMonitoringProfile profile = new CropMonitoringProfile();
        profile.setId(500L + cropId);
        profile.setCropId(cropId);
        profile.setVersion(1);
        profile.setPreferredTemperatureMinCelsius(15.0);
        profile.setPreferredTemperatureMaxCelsius(25.0);
        profile.setTemperatureExcursionSeconds(3600L);
        profile.setTemperatureRecoverySeconds(1800L);
        profile.setSoilMoistureStrategy(strategy);
        profile.setSoilDryThresholdIndex(dry);
        profile.setSoilWetThresholdIndex(wet);
        profile.setSoilMonitoringMode(SoilMonitoringMode.SENSOR);
        profile.setEnabled(true);
        return profile;
    }

    private static CropMonitoringProfile manualProfile(Long cropId) {
        CropMonitoringProfile profile =
                profile(cropId, SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0);
        profile.setVersion(2);
        profile.setSoilMonitoringMode(SoilMonitoringMode.MANUAL);
        return profile;
    }

    private static CropSensorAssignment assignment(Long cropId, String sensorId) {
        CropSensorAssignment assignment = new CropSensorAssignment();
        assignment.setId(900L + cropId);
        assignment.setCropId(cropId);
        assignment.setSensorId(sensorId);
        assignment.setVersion(1);
        return assignment;
    }

    private static GreenhouseTwin twinWithSoil(SoilMoistureTwin... soil) {
        return new GreenhouseTwin(
                GREENHOUSE_ID, "Home Greenhouse", TwinStatus.NORMAL, NOW, NOW, List.of(), List.of(soil)
        );
    }

    private static SensorCalibration calibration(String sensorId) {
        SensorCalibration calibration = new SensorCalibration();
        calibration.setId(77L);
        calibration.setSensorId(sensorId);
        calibration.setVersion(1);
        calibration.setDryReferenceRaw(2814);
        calibration.setWetReferenceRaw(1181);
        return calibration;
    }

    @Test
    void cropWithNoAssignedSensor_reportsNotAssignedRatherThanInferringDryness() {
        Crop tarragon = crop(13L, "Tarragon");
        when(cropRepository.findAll()).thenReturn(List.of(tarragon));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(13L, profile(13L, SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of());

        List<AssessmentFinding> findings = rule.evaluate(twinWithSoil(), NOW);

        assertThat(findings).hasSize(1);
        AssessmentFinding finding = findings.get(0);
        assertThat(finding.code()).isEqualTo(AssessmentCode.CROP_SENSOR_NOT_ASSIGNED);
        assertThat(finding.scopeType()).isEqualTo(AssessmentScopeType.CROP);
        assertThat(finding.scopeId()).isEqualTo("13");
        assertThat(finding.evidence()).containsEntry("reason", "NO_SENSOR_ASSIGNED");
    }

    // The whole point of ADR-024: a crop with no probe BY CHOICE is not a
    // data-quality fault. Before this, it raised an assessment that could never
    // resolve, which held a care loop open forever.
    @Test
    void manuallyMonitoredCropWithNoSensor_raisesNothing() {
        Crop tarragon = crop(13L, "Tarragon");
        when(cropRepository.findAll()).thenReturn(List.of(tarragon));
        when(profileService.enabledProfilesByCropId()).thenReturn(Map.of(13L, manualProfile(13L)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of());

        assertThat(rule.evaluate(twinWithSoil(), NOW)).isEmpty();
    }

    // Suppression must not depend on the sensor also being absent: a manual
    // crop is opted out of sensor assessment entirely, including staleness,
    // calibration and moisture thresholds.
    @Test
    void manuallyMonitoredCropRaisesNothingEvenWithAProbeReporting() {
        Crop tarragon = crop(13L, "Tarragon");
        when(cropRepository.findAll()).thenReturn(List.of(tarragon));
        when(profileService.enabledProfilesByCropId()).thenReturn(Map.of(13L, manualProfile(13L)));
        lenient().when(assignmentService.currentAssignmentsByCropId())
                .thenReturn(Map.of(13L, assignment(13L, "soil-06")));

        // A reading that would comfortably breach the wet threshold if this
        // crop were sensor-assessed.
        SoilMoistureTwin soaked = new SoilMoistureTwin(
                "soil-06", 1181, NOW, 10L, FreshnessStatus.CURRENT);

        assertThat(rule.evaluate(twinWithSoil(soaked), NOW)).isEmpty();
    }

    @Test
    void switchingBackToSensorRestoresNormalAssessment() {
        Crop tarragon = crop(13L, "Tarragon");
        when(cropRepository.findAll()).thenReturn(List.of(tarragon));
        // The same crop, now back on a SENSOR profile with still no probe.
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(13L, profile(13L, SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of());

        List<AssessmentFinding> findings = rule.evaluate(twinWithSoil(), NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.CROP_SENSOR_NOT_ASSIGNED);
    }

    @Test
    void aManualCropDoesNotSuppressAssessmentForItsSensorEquippedNeighbours() {
        Crop tarragon = crop(13L, "Tarragon");
        Crop basil = crop(8L, "Basil");
        when(cropRepository.findAll()).thenReturn(List.of(tarragon, basil));
        when(profileService.enabledProfilesByCropId()).thenReturn(Map.of(
                13L, manualProfile(13L),
                8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of());

        List<AssessmentFinding> findings = rule.evaluate(twinWithSoil(), NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).scopeId()).isEqualTo("8");
    }

    @Test
    void uncalibratedSensor_reportsCalibrationRequiredAndDrawsNoConclusion() {
        Crop basil = crop(8L, "Basil");
        when(cropRepository.findAll()).thenReturn(List.of(basil));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(8L, assignment(8L, "soil-01")));
        when(calibrationService.findCurrentCalibration("soil-01")).thenReturn(Optional.empty());

        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-01", 2800, NOW, 30L, FreshnessStatus.CURRENT));

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.CROP_SENSOR_CALIBRATION_REQUIRED);
        assertThat(findings.get(0).evidence()).containsEntry("reason", "CALIBRATION_REQUIRED");
    }

    @Test
    void staleReading_reportsStaleRatherThanUsingTheOldValue() {
        Crop basil = crop(8L, "Basil");
        when(cropRepository.findAll()).thenReturn(List.of(basil));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(8L, assignment(8L, "soil-01")));

        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-01", 2800, NOW.minusSeconds(3600), 3600L, FreshnessStatus.STALE));

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.CROP_SENSOR_DATA_STALE);
        assertThat(findings.get(0).severity()).isEqualTo(AssessmentSeverity.WARNING);
    }

    @Test
    void moistureLovingCropBelowDryThreshold_raisesLowAsAWarning() {
        Crop basil = crop(8L, "Basil");
        when(cropRepository.findAll()).thenReturn(List.of(basil));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(8L, assignment(8L, "soil-01")));
        when(calibrationService.findCurrentCalibration("soil-01")).thenReturn(Optional.of(calibration("soil-01")));
        when(calibrationService.calculateIndex(any(SensorCalibration.class), anyInt()))
                .thenReturn(new MoistureIndex(12.0, 2700, 77L, 1));

        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-01", 2700, NOW, 30L, FreshnessStatus.CURRENT));

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        AssessmentFinding finding = findings.get(0);
        assertThat(finding.code()).isEqualTo(AssessmentCode.CROP_SOIL_MOISTURE_LOW);
        // Drying out is the real risk for an evenly-moist herb.
        assertThat(finding.severity()).isEqualTo(AssessmentSeverity.WARNING);
        assertThat(finding.calibrationId()).isEqualTo(77L);
        assertThat(finding.calibrationVersion()).isEqualTo(1);
        assertThat(finding.cropId()).isEqualTo(8L);
    }

    @Test
    void dryLeaningCropAboveWetThreshold_raisesHighAsAWarning() {
        Crop sage = crop(11L, "Sage");
        when(cropRepository.findAll()).thenReturn(List.of(sage));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(11L, profile(11L, SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(11L, assignment(11L, "soil-04")));
        when(calibrationService.findCurrentCalibration("soil-04")).thenReturn(Optional.of(calibration("soil-04")));
        when(calibrationService.calculateIndex(any(SensorCalibration.class), anyInt()))
                .thenReturn(new MoistureIndex(88.0, 1300, 77L, 1));

        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-04", 1300, NOW, 30L, FreshnessStatus.CURRENT));

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        // Sitting wet is the real risk for a dry-leaning herb.
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.CROP_SOIL_MOISTURE_HIGH);
        assertThat(findings.get(0).severity()).isEqualTo(AssessmentSeverity.WARNING);
    }

    @Test
    void moistureWithinThresholds_producesNoFinding() {
        Crop basil = crop(8L, "Basil");
        when(cropRepository.findAll()).thenReturn(List.of(basil));
        when(profileService.enabledProfilesByCropId())
                .thenReturn(Map.of(8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null)));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(8L, assignment(8L, "soil-01")));
        when(calibrationService.findCurrentCalibration("soil-01")).thenReturn(Optional.of(calibration("soil-01")));
        when(calibrationService.calculateIndex(any(SensorCalibration.class), anyInt()))
                .thenReturn(new MoistureIndex(55.0, 1900, 77L, 1));

        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-01", 1900, NOW, 30L, FreshnessStatus.CURRENT));

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void oneFailedSensorDoesNotBlockTheOthers() {
        Crop basil = crop(8L, "Basil");
        Crop sage = crop(11L, "Sage");
        when(cropRepository.findAll()).thenReturn(List.of(basil, sage));
        when(profileService.enabledProfilesByCropId()).thenReturn(Map.of(
                8L, profile(8L, SoilMoistureStrategy.EVENLY_MOIST, 30.0, null),
                11L, profile(11L, SoilMoistureStrategy.DRY_BETWEEN_WATERING, 15.0, 75.0)
        ));
        when(assignmentService.currentAssignmentsByCropId()).thenReturn(Map.of(
                8L, assignment(8L, "soil-01"),
                11L, assignment(11L, "soil-04")
        ));
        lenient().when(calibrationService.findCurrentCalibration("soil-04"))
                .thenReturn(Optional.of(calibration("soil-04")));
        lenient().when(calibrationService.calculateIndex(any(SensorCalibration.class), anyInt()))
                .thenReturn(new MoistureIndex(50.0, 1900, 77L, 1));

        // soil-01 is stale; soil-04 is fine and must still be evaluated.
        GreenhouseTwin twin = twinWithSoil(
                new SoilMoistureTwin("soil-01", 2800, NOW.minusSeconds(3600), 3600L, FreshnessStatus.STALE),
                new SoilMoistureTwin("soil-04", 1900, NOW, 30L, FreshnessStatus.CURRENT)
        );

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.CROP_SENSOR_DATA_STALE);
        assertThat(findings.get(0).cropId()).isEqualTo(8L);
    }

    @Test
    void endedCropsAreIgnored() {
        Crop ended = crop(99L, "Basil");
        ended.setStatus(CropStatus.ENDED);
        when(cropRepository.findAll()).thenReturn(List.of(ended));

        // The rule returns before consulting profiles or assignments at all,
        // which is why neither is stubbed here.
        assertThat(rule.evaluate(twinWithSoil(), NOW)).isEmpty();
    }
}
