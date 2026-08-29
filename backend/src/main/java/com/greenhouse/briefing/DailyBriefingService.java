package com.greenhouse.briefing;

import com.greenhouse.assessment.AssessmentLifecycleEvent;
import com.greenhouse.assessment.AssessmentLifecycleEventRepository;
import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.careloop.CareLoopQueryService;
import com.greenhouse.careloop.OpenCareLoopSummary;
import com.greenhouse.careloop.outcome.Outcome;
import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropMonitoringProfile;
import com.greenhouse.crop.CropMonitoringProfileService;
import com.greenhouse.crop.CropObservationService;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropStatus;
import com.greenhouse.crop.HarvestService;
import com.greenhouse.observation.ObservationService;
import com.greenhouse.observation.ObservationStatus;
import com.greenhouse.observation.assignment.CropSensorAssignment;
import com.greenhouse.observation.assignment.CropSensorAssignmentService;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import com.greenhouse.twin.TwinService;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.SoilMoistureTwin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Assembles the structured daily briefing: measured facts, assessments, open
// loops, and explicit data-quality gaps.
//
// The backend produces evidence only - it never writes prose and never calls
// an LLM. Turning this into a readable morning update is Claude's job, from
// this structure.
@Service
public class DailyBriefingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyBriefingService.class);

    private final DailyBriefingSnapshotRepository snapshotRepository;
    private final DailyBriefingProperties properties;
    private final TwinService twinService;
    private final ObservationService observationService;
    private final CropRepository cropRepository;
    private final CropMonitoringProfileService profileService;
    private final CropSensorAssignmentService assignmentService;
    private final SensorCalibrationService calibrationService;
    private final CropObservationService cropObservationService;
    private final HarvestService harvestService;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentLifecycleEventRepository lifecycleEventRepository;
    private final AssessmentMapper assessmentMapper;
    private final CareLoopQueryService careLoopQueryService;
    private final Clock clock;

    public DailyBriefingService(
            DailyBriefingSnapshotRepository snapshotRepository,
            DailyBriefingProperties properties,
            TwinService twinService,
            ObservationService observationService,
            CropRepository cropRepository,
            CropMonitoringProfileService profileService,
            CropSensorAssignmentService assignmentService,
            SensorCalibrationService calibrationService,
            CropObservationService cropObservationService,
            HarvestService harvestService,
            AssessmentRepository assessmentRepository,
            AssessmentLifecycleEventRepository lifecycleEventRepository,
            AssessmentMapper assessmentMapper,
            CareLoopQueryService careLoopQueryService,
            Clock clock
    ) {
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.twinService = twinService;
        this.observationService = observationService;
        this.cropRepository = cropRepository;
        this.profileService = profileService;
        this.assignmentService = assignmentService;
        this.calibrationService = calibrationService;
        this.cropObservationService = cropObservationService;
        this.harvestService = harvestService;
        this.assessmentRepository = assessmentRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.assessmentMapper = assessmentMapper;
        this.careLoopQueryService = careLoopQueryService;
        this.clock = clock;
    }

    // Generates today's snapshot if it does not already exist. Safe to call on
    // startup and on schedule: the existence check is what makes missed-run
    // recovery idempotent rather than duplicating.
    @Transactional
    public Optional<DailyBriefingSnapshot> generateIfMissing(boolean missedRunRecovery) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), properties.zoneId());

        if (snapshotRepository.existsByGreenhouseDay(today)) {
            return Optional.empty();
        }

        return Optional.of(generate(today, missedRunRecovery, null));
    }

    // Explicit regeneration creates a NEW version linked to the previous one;
    // the earlier snapshot is never overwritten.
    @Transactional
    public DailyBriefingSnapshot regenerate(LocalDate greenhouseDay) {
        Long supersedes = snapshotRepository
                .findFirstByGreenhouseDayOrderByGeneratedAtDescIdDesc(greenhouseDay)
                .map(DailyBriefingSnapshot::getId)
                .orElse(null);
        return generate(greenhouseDay, false, supersedes);
    }

    private DailyBriefingSnapshot generate(LocalDate greenhouseDay, boolean missedRunRecovery, Long supersedesId) {
        Instant now = clock.instant();
        ZonedDateTime scheduled = greenhouseDay.atTime(properties.generateAt()).atZone(properties.zoneId());
        Instant windowEnd = now;
        Instant windowStart = now.minus(properties.window());

        DailyBriefingSnapshot snapshot = new DailyBriefingSnapshot();
        snapshot.setGreenhouseDay(greenhouseDay);
        snapshot.setScheduledFor(scheduled.toInstant());
        snapshot.setGeneratedAt(now);
        snapshot.setWindowStart(windowStart);
        snapshot.setWindowEnd(windowEnd);
        snapshot.setMissedRunRecovery(missedRunRecovery);
        snapshot.setSupersedesSnapshotId(supersedesId);
        snapshot.setSnapshot(buildBriefing(windowStart, windowEnd, now));

        DailyBriefingSnapshot saved = snapshotRepository.save(snapshot);
        LOGGER.info(
                "Daily briefing generated: id={} day={} missedRunRecovery={}",
                saved.getId(), greenhouseDay, missedRunRecovery
        );
        return saved;
    }

    // Computed live rather than read from a snapshot - used by the MCP tool
    // when no snapshot exists yet, so a fresh install still answers usefully.
    public Map<String, Object> buildCurrentBriefing() {
        Instant now = clock.instant();
        return buildBriefing(now.minus(properties.window()), now, now);
    }

    private Map<String, Object> buildBriefing(Instant windowStart, Instant windowEnd, Instant now) {
        GreenhouseTwin twin = twinService.getCurrentTwin();

        Map<String, Object> briefing = new LinkedHashMap<>();
        briefing.put("generatedAt", now.toString());
        briefing.put("windowStart", windowStart.toString());
        briefing.put("windowEnd", windowEnd.toString());
        briefing.put("greenhouse", greenhouseConditions(twin));
        briefing.put("crops", cropEntries(twin, windowStart));
        briefing.put("openCareLoops", careLoopQueryService.openLoops(null, null).stream()
                .map(this::loopEntry).toList());
        briefing.put("assessmentActivity", assessmentActivity(windowStart));
        briefing.put("recentOutcomes", careLoopQueryService.recentOutcomes(windowStart).stream()
                .map(this::outcomeEntry).toList());
        briefing.put("dataQualityGaps", dataQualityGaps(twin));
        briefing.put("note", "Structured evidence only. Moisture index is a 0-100 position between each "
                + "probe's own dry and wet references, not a volumetric water percentage.");

        return briefing;
    }

    private Map<String, Object> greenhouseConditions(GreenhouseTwin twin) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("status", String.valueOf(twin.status()));
        conditions.put("lastUpdatedAt", String.valueOf(twin.lastUpdatedAt()));

        twin.zones().stream().findFirst().ifPresent(zone -> {
            conditions.put("zoneId", zone.zoneId());
            conditions.put("temperatureCelsius", zone.environment().temperatureCelsius());
            conditions.put("humidityPercent", zone.environment().humidityPercent());
            conditions.put("pressureHpa", zone.environment().pressureHpa());
            conditions.put("freshness", String.valueOf(zone.dataQuality().freshness()));
            conditions.put("observationAgeSeconds", zone.dataQuality().ageSeconds());
        });

        return conditions;
    }

    private List<Map<String, Object>> cropEntries(GreenhouseTwin twin, Instant windowStart) {
        Map<String, SoilMoistureTwin> soilBySensor = new HashMap<>();
        twin.soilMoisture().forEach(soil -> soilBySensor.put(soil.sensorId(), soil));

        Map<Long, CropMonitoringProfile> profiles = profileService.enabledProfilesByCropId();
        Map<Long, CropSensorAssignment> assignments = assignmentService.currentAssignmentsByCropId();

        // Every active crop appears, including ones with no usable data - a
        // crop missing from the briefing would read as "nothing to report"
        // when the truth is "we cannot tell".
        return cropRepository.findAll().stream()
                .filter(crop -> crop.getStatus() != CropStatus.ENDED)
                .sorted(Comparator.comparing(Crop::getId))
                .map(crop -> cropEntry(crop, profiles.get(crop.getId()),
                        assignments.get(crop.getId()), soilBySensor, windowStart))
                .toList();
    }

    private Map<String, Object> cropEntry(
            Crop crop,
            CropMonitoringProfile profile,
            CropSensorAssignment assignment,
            Map<String, SoilMoistureTwin> soilBySensor,
            Instant windowStart
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("cropId", crop.getId());
        entry.put("species", crop.getSpecies());
        entry.put("variety", crop.getVariety());
        entry.put("status", String.valueOf(crop.getStatus()));

        if (profile != null) {
            Map<String, Object> preferences = new LinkedHashMap<>();
            preferences.put("preferredTemperatureMinCelsius", profile.getPreferredTemperatureMinCelsius());
            preferences.put("preferredTemperatureMaxCelsius", profile.getPreferredTemperatureMaxCelsius());
            preferences.put("soilMoistureStrategy", String.valueOf(profile.getSoilMoistureStrategy()));
            preferences.put("soilDryThresholdIndex", profile.getSoilDryThresholdIndex());
            preferences.put("soilWetThresholdIndex", profile.getSoilWetThresholdIndex());
            preferences.put("profileVersion", profile.getVersion());
            entry.put("preferences", preferences);
        } else {
            entry.put("preferences", null);
            entry.put("preferencesNote", "No monitoring profile configured, so no thresholds are applied.");
        }

        entry.put("soil", soilEntry(assignment, soilBySensor));

        entry.put("assessments", assessmentRepository.findAllByStatus(AssessmentStatus.ACTIVE).stream()
                .filter(assessment -> crop.getId().equals(assessment.getCropId()))
                .map(assessmentMapper::toResponse)
                .toList());

        entry.put("latestObservation", cropObservationService.getObservationHistory(crop.getId()).stream()
                .reduce((first, second) -> second).orElse(null));
        entry.put("latestHarvest", harvestService.getHarvestHistory(crop.getId()).stream()
                .reduce((first, second) -> second).orElse(null));

        return entry;
    }

    private Map<String, Object> soilEntry(
            CropSensorAssignment assignment,
            Map<String, SoilMoistureTwin> soilBySensor
    ) {
        Map<String, Object> soil = new LinkedHashMap<>();

        if (assignment == null) {
            soil.put("status", "UNKNOWN");
            soil.put("reason", "NO_SENSOR_ASSIGNED");
            soil.put("note", "This crop has no soil probe; its soil state can only be judged by looking at it.");
            return soil;
        }

        String sensorId = assignment.getSensorId();
        soil.put("sensorId", sensorId);

        SoilMoistureTwin reading = soilBySensor.get(sensorId);
        if (reading == null || reading.rawAdc() == null) {
            soil.put("status", "UNKNOWN");
            soil.put("reason", "NO_READING");
            return soil;
        }

        soil.put("rawAdc", reading.rawAdc());
        soil.put("observedAt", String.valueOf(reading.observedAt()));
        soil.put("ageSeconds", reading.ageSeconds());
        soil.put("freshness", String.valueOf(reading.freshness()));

        Optional<SensorCalibration> calibration = calibrationService.findCurrentCalibration(sensorId);
        if (calibration.isEmpty()) {
            soil.put("status", "UNKNOWN");
            soil.put("reason", "CALIBRATION_REQUIRED");
            return soil;
        }

        soil.put("status", "MEASURED");
        soil.put("moistureIndex",
                calibrationService.calculateIndex(calibration.get(), reading.rawAdc()).value());
        soil.put("calibrationVersion", calibration.get().getVersion());
        return soil;
    }

    private Map<String, Object> loopEntry(OpenCareLoopSummary summary) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("careLoopId", summary.id());
        entry.put("subjectType", String.valueOf(summary.primarySubjectType()));
        entry.put("subjectId", summary.primarySubjectId());
        entry.put("condition", summary.conditionType());
        entry.put("status", String.valueOf(summary.status()));
        entry.put("nextRequiredAction", summary.nextRequiredAction());
        entry.put("openedAt", String.valueOf(summary.openedAt()));
        entry.put("pendingDecisionId", summary.pendingDecisionId());
        entry.put("pendingCommandId", summary.pendingCommandId());
        return entry;
    }

    private Map<String, Object> outcomeEntry(Outcome outcome) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("outcomeId", outcome.getId());
        entry.put("careLoopId", outcome.getCareLoopId());
        entry.put("result", String.valueOf(outcome.getResult()));
        entry.put("summary", outcome.getSummary());
        entry.put("evaluatedAt", String.valueOf(outcome.getEvaluatedAt()));
        entry.put("supersedesOutcomeId", outcome.getSupersedesOutcomeId());
        return entry;
    }

    private Map<String, Object> assessmentActivity(Instant windowStart) {
        List<AssessmentLifecycleEvent> events =
                lifecycleEventRepository.findAllByOccurredAtAfterOrderByOccurredAtDesc(windowStart);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("eventsInWindow", events.size());
        activity.put("events", events.stream().limit(50).map(event -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("assessmentId", event.getAssessmentId());
            entry.put("eventType", String.valueOf(event.getEventType()));
            entry.put("code", String.valueOf(event.getCode()));
            entry.put("severity", String.valueOf(event.getSeverity()));
            entry.put("cropId", event.getCropId());
            entry.put("occurredAt", String.valueOf(event.getOccurredAt()));
            return entry;
        }).toList());
        return activity;
    }

    // Stated explicitly rather than left as absence, so a briefing can never
    // imply "all well" when the truth is "we could not measure".
    private List<Map<String, Object>> dataQualityGaps(GreenhouseTwin twin) {
        List<Map<String, Object>> gaps = new ArrayList<>();

        twin.zones().forEach(zone -> {
            String freshness = String.valueOf(zone.dataQuality().freshness());
            if (!"CURRENT".equals(freshness)) {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("kind", "ENVIRONMENT_DATA");
                gap.put("zoneId", zone.zoneId());
                gap.put("freshness", freshness);
                gap.put("ageSeconds", zone.dataQuality().ageSeconds());
                gaps.add(gap);
            }
        });

        Map<Long, CropSensorAssignment> assignments = assignmentService.currentAssignmentsByCropId();
        cropRepository.findAll().stream()
                .filter(crop -> crop.getStatus() != CropStatus.ENDED)
                .forEach(crop -> {
                    if (!assignments.containsKey(crop.getId())) {
                        Map<String, Object> gap = new LinkedHashMap<>();
                        gap.put("kind", "NO_SENSOR_ASSIGNED");
                        gap.put("cropId", crop.getId());
                        gap.put("species", crop.getSpecies());
                        gaps.add(gap);
                    }
                });

        twin.soilMoisture().forEach(soil -> {
            if (calibrationService.findCurrentCalibration(soil.sensorId()).isEmpty()) {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("kind", "CALIBRATION_REQUIRED");
                gap.put("sensorId", soil.sensorId());
                gaps.add(gap);
            }
            if (!"CURRENT".equals(String.valueOf(soil.freshness()))) {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("kind", "SOIL_DATA_STALE");
                gap.put("sensorId", soil.sensorId());
                gap.put("freshness", String.valueOf(soil.freshness()));
                gap.put("ageSeconds", soil.ageSeconds());
                gaps.add(gap);
            }
        });

        return gaps;
    }

    public Optional<DailyBriefingSnapshot> latestSnapshot() {
        return snapshotRepository.findFirstByOrderByGeneratedAtDescIdDesc();
    }

    public Optional<DailyBriefingSnapshot> snapshotForDay(LocalDate day) {
        return snapshotRepository.findFirstByGreenhouseDayOrderByGeneratedAtDescIdDesc(day);
    }
}
