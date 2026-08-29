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
import com.greenhouse.observation.assignment.CropSensorAssignment;
import com.greenhouse.observation.assignment.CropSensorAssignmentService;
import com.greenhouse.observation.calibration.MoistureIndex;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.SoilMoistureTwin;
import com.greenhouse.twin.status.FreshnessStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Turns a raw ADC fact into a crop-specific soil verdict, in four steps that
// each have an honest failure mode:
//
//   no assignment    -> CROP_SENSOR_NOT_ASSIGNED    (never inferred as dry)
//   no calibration   -> CROP_SENSOR_CALIBRATION_REQUIRED
//   stale reading    -> CROP_SENSOR_DATA_STALE
//   otherwise        -> index vs the crop's own thresholds
//
// The three data-quality codes exist precisely so that missing information is
// reported as missing rather than silently becoming a watering recommendation
// (ADR-021).
@Component
public class CropSoilMoistureAssessmentRule implements AssessmentRule {

    private static final String RULE_ID = "crop-soil-moisture";
    private static final int RULE_VERSION = 1;

    private final CropRepository cropRepository;
    private final CropMonitoringProfileService profileService;
    private final CropSensorAssignmentService assignmentService;
    private final SensorCalibrationService calibrationService;
    private final TwinProperties twinProperties;
    private final AssessmentCorrelationKeyFactory correlationKeyFactory;

    public CropSoilMoistureAssessmentRule(
            CropRepository cropRepository,
            CropMonitoringProfileService profileService,
            CropSensorAssignmentService assignmentService,
            SensorCalibrationService calibrationService,
            TwinProperties twinProperties,
            AssessmentCorrelationKeyFactory correlationKeyFactory
    ) {
        this.cropRepository = cropRepository;
        this.profileService = profileService;
        this.assignmentService = assignmentService;
        this.calibrationService = calibrationService;
        this.twinProperties = twinProperties;
        this.correlationKeyFactory = correlationKeyFactory;
    }

    @Override
    public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
        List<Crop> activeCrops = cropRepository.findAll().stream()
                .filter(crop -> crop.getStatus() != CropStatus.ENDED)
                .toList();
        if (activeCrops.isEmpty()) {
            return List.of();
        }

        Map<Long, CropMonitoringProfile> profiles = profileService.enabledProfilesByCropId();
        Map<Long, CropSensorAssignment> assignments = assignmentService.currentAssignmentsByCropId();
        Map<String, SoilMoistureTwin> soilBySensor = new HashMap<>();
        for (SoilMoistureTwin soil : twin.soilMoisture()) {
            soilBySensor.put(soil.sensorId(), soil);
        }

        List<AssessmentFinding> findings = new ArrayList<>();

        for (Crop crop : activeCrops) {
            CropMonitoringProfile profile = profiles.get(crop.getId());
            if (profile == null) {
                continue;
            }

            CropSensorAssignment assignment = assignments.get(crop.getId());
            if (assignment == null) {
                findings.add(notAssigned(crop, profile));
                continue;
            }

            SoilMoistureTwin soil = soilBySensor.get(assignment.getSensorId());
            if (soil == null || soil.rawAdc() == null) {
                findings.add(stale(crop, profile, assignment.getSensorId(), null));
                continue;
            }

            if (soil.freshness() == FreshnessStatus.STALE || soil.freshness() == FreshnessStatus.UNKNOWN) {
                findings.add(stale(crop, profile, assignment.getSensorId(), soil));
                continue;
            }

            Optional<SensorCalibration> calibration =
                    calibrationService.findCurrentCalibration(assignment.getSensorId());
            if (calibration.isEmpty()) {
                findings.add(calibrationRequired(crop, profile, assignment.getSensorId(), soil));
                continue;
            }

            MoistureIndex index = calibrationService.calculateIndex(calibration.get(), soil.rawAdc());
            moistureFinding(crop, profile, assignment.getSensorId(), soil, index).ifPresent(findings::add);
        }

        return findings;
    }

    private Optional<AssessmentFinding> moistureFinding(
            Crop crop,
            CropMonitoringProfile profile,
            String sensorId,
            SoilMoistureTwin soil,
            MoistureIndex index
    ) {
        Double dryThreshold = profile.getSoilDryThresholdIndex();
        Double wetThreshold = profile.getSoilWetThresholdIndex();

        AssessmentCode code;
        String message;
        if (dryThreshold != null && index.value() <= dryThreshold) {
            code = AssessmentCode.CROP_SOIL_MOISTURE_LOW;
            message = String.format(
                    Locale.ROOT,
                    "%s (crop %d) soil moisture index is %.0f, at or below its dry threshold of %.0f.",
                    crop.getSpecies(), crop.getId(), index.value(), dryThreshold
            );
        } else if (wetThreshold != null && index.value() >= wetThreshold) {
            code = AssessmentCode.CROP_SOIL_MOISTURE_HIGH;
            message = String.format(
                    Locale.ROOT,
                    "%s (crop %d) soil moisture index is %.0f, at or above its wet threshold of %.0f.",
                    crop.getSpecies(), crop.getId(), index.value(), wetThreshold
            );
        } else {
            return Optional.empty();
        }

        Map<String, Object> evidence = new HashMap<>();
        evidence.put("moistureIndex", index.value());
        evidence.put("rawAdc", index.rawAdc());
        evidence.put("sensorId", sensorId);
        evidence.put("soilMoistureStrategy", profile.getSoilMoistureStrategy().name());
        evidence.put("dryThresholdIndex", dryThreshold);
        evidence.put("wetThresholdIndex", wetThreshold);
        evidence.put("observationReceivedAt", String.valueOf(soil.observedAt()));
        evidence.put("observationAgeSeconds", soil.ageSeconds());
        evidence.put("note", "Moisture index is a 0-100 position between this probe's own dry and wet "
                + "references, not a volumetric water percentage.");

        return Optional.of(finding(crop, profile, code, severityFor(profile, code), message, evidence,
                index.calibrationId(), index.calibrationVersion()));
    }

    // A dry-leaning herb sitting wet, or a moisture-loving herb drying out, is
    // the condition that actually matters for that crop; the opposite case is
    // advisory.
    private AssessmentSeverity severityFor(CropMonitoringProfile profile, AssessmentCode code) {
        boolean dryLeaning = profile.getSoilMoistureStrategy() == SoilMoistureStrategy.DRY_BETWEEN_WATERING;
        boolean concerning = dryLeaning
                ? code == AssessmentCode.CROP_SOIL_MOISTURE_HIGH
                : code == AssessmentCode.CROP_SOIL_MOISTURE_LOW;
        return concerning ? AssessmentSeverity.WARNING : AssessmentSeverity.ADVISORY;
    }

    private AssessmentFinding notAssigned(Crop crop, CropMonitoringProfile profile) {
        return finding(
                crop, profile,
                AssessmentCode.CROP_SENSOR_NOT_ASSIGNED,
                AssessmentSeverity.ADVISORY,
                String.format(Locale.ROOT,
                        "%s (crop %d) has no soil moisture probe assigned; its soil state is unknown and must be "
                                + "judged by manual observation.",
                        crop.getSpecies(), crop.getId()),
                Map.of("reason", "NO_SENSOR_ASSIGNED"),
                null, null
        );
    }

    private AssessmentFinding calibrationRequired(
            Crop crop, CropMonitoringProfile profile, String sensorId, SoilMoistureTwin soil
    ) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("reason", "CALIBRATION_REQUIRED");
        evidence.put("sensorId", sensorId);
        evidence.put("rawAdc", soil.rawAdc());
        evidence.put("note", "Raw readings are retained but cannot be converted to a moisture index "
                + "without calibration, so no dry/wet conclusion is drawn.");

        return finding(
                crop, profile,
                AssessmentCode.CROP_SENSOR_CALIBRATION_REQUIRED,
                AssessmentSeverity.ADVISORY,
                String.format(Locale.ROOT,
                        "%s (crop %d) probe %s has no calibration, so its soil state cannot be interpreted.",
                        crop.getSpecies(), crop.getId(), sensorId),
                evidence,
                null, null
        );
    }

    private AssessmentFinding stale(
            Crop crop, CropMonitoringProfile profile, String sensorId, SoilMoistureTwin soil
    ) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("reason", "SENSOR_DATA_STALE");
        evidence.put("sensorId", sensorId);
        evidence.put("observationReceivedAt", soil == null ? null : String.valueOf(soil.observedAt()));
        evidence.put("observationAgeSeconds", soil == null ? null : soil.ageSeconds());

        return finding(
                crop, profile,
                AssessmentCode.CROP_SENSOR_DATA_STALE,
                AssessmentSeverity.WARNING,
                String.format(Locale.ROOT,
                        "%s (crop %d) probe %s has no recent reading, so its soil state is unknown.",
                        crop.getSpecies(), crop.getId(), sensorId),
                evidence,
                null, null
        );
    }

    private AssessmentFinding finding(
            Crop crop,
            CropMonitoringProfile profile,
            AssessmentCode code,
            AssessmentSeverity severity,
            String message,
            Map<String, Object> evidence,
            Long calibrationId,
            Integer calibrationVersion
    ) {
        String correlationKey = correlationKeyFactory.create(
                twinProperties.greenhouseId(), AssessmentScopeType.CROP, String.valueOf(crop.getId()), code
        );

        return new AssessmentFinding(
                code,
                severity,
                AssessmentScopeType.CROP,
                String.valueOf(crop.getId()),
                twinProperties.greenhouseId(),
                null,
                null,
                message,
                evidence,
                RULE_ID,
                RULE_VERSION,
                correlationKey,
                crop.getId(),
                profile.getId(),
                profile.getVersion(),
                calibrationId,
                calibrationVersion
        );
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public int ruleVersion() {
        return RULE_VERSION;
    }
}
