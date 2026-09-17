package com.greenhouse.briefing;

import com.greenhouse.briefing.summary.BriefingSummary;
import com.greenhouse.briefing.summary.BriefingSummaryService;
import com.greenhouse.assessment.AssessmentLifecycleEvent;
import com.greenhouse.assessment.AssessmentLifecycleEventRepository;
import com.greenhouse.assessment.AssessmentMapper;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentResponse;
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
import com.greenhouse.crop.SoilMonitoringMode;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final CropSoilTrendService trendService;
    private final BriefingSummaryService summaryService;
    private final com.greenhouse.action.ActionService actionService;
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
            CropSoilTrendService trendService,
            BriefingSummaryService summaryService,
            com.greenhouse.action.ActionService actionService,
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
        this.trendService = trendService;
        this.summaryService = summaryService;
        this.actionService = actionService;
        this.clock = clock;
    }

    // Generates whichever editions are genuinely due: at or after their local
    // time, not already present, and not yet overtaken by the next edition.
    //
    // The time check matters as much as the existence check. Without it, any
    // restart produced a "daily briefing" stamped with today's schedule but
    // actually generated at whatever hour the process happened to start - which
    // is exactly what happened in production, where a deploy at 23:59 created
    // that day's briefing four minutes before midnight.
    //
    // The STALENESS check is the second half of the same idea, and it is what
    // stops a restart at 20:00 sending two briefings at once: a morning
    // briefing generated after the evening one is due is not a recovered
    // briefing, it is a wrong one (ADR-033).
    //
    // Safe to call repeatedly from both the scheduler and startup recovery.
    @Transactional
    public List<DailyBriefingSnapshot> generateIfDue(boolean missedRunRecovery) {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), properties.zoneId());
        LocalDate today = now.toLocalDate();

        List<DailyBriefingSnapshot> generated = new ArrayList<>();
        // Yesterday's evening edition is still the current one until this
        // morning's is due, so an overnight restart recovers it rather than
        // skipping straight to today.
        generateIfCurrent(BriefingEdition.EVENING, today.minusDays(1), now, missedRunRecovery)
                .ifPresent(generated::add);
        generateIfCurrent(BriefingEdition.MORNING, today, now, missedRunRecovery)
                .ifPresent(generated::add);
        generateIfCurrent(BriefingEdition.EVENING, today, now, missedRunRecovery)
                .ifPresent(generated::add);
        return generated;
    }

    private Optional<DailyBriefingSnapshot> generateIfCurrent(
            BriefingEdition edition, LocalDate day, ZonedDateTime now, boolean missedRunRecovery
    ) {
        if (snapshotRepository.existsByGreenhouseDayAndEdition(day, edition)) {
            return Optional.empty();
        }
        if (now.isBefore(properties.scheduledFor(edition, day))) {
            // Not late - it simply has not come round yet.
            return Optional.empty();
        }
        if (!now.isBefore(properties.staleAfter(edition, day))) {
            // The next edition is already due; this one is history. A day may
            // therefore end with only one briefing, which is the honest record
            // of what happened rather than a retrospective invention.
            return Optional.empty();
        }
        return Optional.of(generate(day, edition, missedRunRecovery, null));
    }

    // Explicit regeneration creates a NEW version linked to the previous one;
    // the earlier snapshot is never overwritten.
    @Transactional
    public DailyBriefingSnapshot regenerate(LocalDate greenhouseDay, BriefingEdition edition) {
        Long supersedes = snapshotRepository
                .findFirstByGreenhouseDayAndEditionOrderByGeneratedAtDescIdDesc(greenhouseDay, edition)
                .map(DailyBriefingSnapshot::getId)
                .orElse(null);
        return generate(greenhouseDay, edition, false, supersedes);
    }

    private DailyBriefingSnapshot generate(
            LocalDate greenhouseDay, BriefingEdition edition, boolean missedRunRecovery, Long supersedesId
    ) {
        Instant now = clock.instant();
        ZonedDateTime scheduled = properties.scheduledFor(edition, greenhouseDay);
        Instant windowEnd = now;
        // Since the PREVIOUS edition, not a fixed 24 hours - which is what lets
        // the evening briefing answer "what changed while I was out" rather
        // than repeating the morning (ADR-033).
        Instant windowStart = properties.windowStartFor(edition, greenhouseDay).toInstant();

        DailyBriefingSnapshot snapshot = new DailyBriefingSnapshot();
        snapshot.setGreenhouseDay(greenhouseDay);
        snapshot.setEdition(edition);
        snapshot.setScheduledFor(scheduled.toInstant());
        snapshot.setGeneratedAt(now);
        snapshot.setWindowStart(windowStart);
        snapshot.setWindowEnd(windowEnd);
        snapshot.setMissedRunRecovery(missedRunRecovery);
        snapshot.setSupersedesSnapshotId(supersedesId);
        snapshot.setSnapshot(buildBriefing(windowStart, windowEnd, now, edition));

        DailyBriefingSnapshot saved = snapshotRepository.save(snapshot);
        LOGGER.info(
                "Daily briefing generated: id={} day={} edition={} missedRunRecovery={}",
                saved.getId(), greenhouseDay, edition, missedRunRecovery
        );
        return saved;
    }

    // Computed live rather than read from a snapshot - used by the MCP tool
    // when no snapshot exists yet, so a fresh install still answers usefully.
    public Map<String, Object> buildCurrentBriefing() {
        Instant now = clock.instant();
        return buildBriefing(now.minus(properties.window()), now, now, BriefingEdition.MORNING);
    }

    private Map<String, Object> buildBriefing(
            Instant windowStart, Instant windowEnd, Instant now, BriefingEdition edition) {
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
        briefing.put("note", "Moisture index is a 0-100 position between each probe's own dry and wet "
                + "references, not a volumetric water percentage.");

        // Composed last, from the structured briefing that precedes it, so the
        // prose can never describe something the evidence does not contain.
        briefing.put("edition", edition.name());
        briefing.put("summary", summarySection(briefing, twin, windowStart, edition));

        return briefing;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summarySection(
            Map<String, Object> briefing, GreenhouseTwin twin, Instant windowStart, BriefingEdition edition
    ) {
        List<Map<String, Object>> crops = (List<Map<String, Object>>) briefing.get("crops");
        List<Map<String, Object>> loops = (List<Map<String, Object>>) briefing.get("openCareLoops");
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) briefing.get("dataQualityGaps");

        List<String> cropLines = new ArrayList<>();
        List<String> attentionNames = new ArrayList<>();
        List<String> warningLines = new ArrayList<>();

        for (Map<String, Object> crop : crops) {
            cropLines.add((String) crop.get("factLine"));
            List<AssessmentResponse> assessments =
                    (List<AssessmentResponse>) crop.getOrDefault("assessments", List.of());
            if (!assessments.isEmpty()) {
                attentionNames.add(String.valueOf(crop.get("species")));
                assessments.forEach(assessment -> warningLines.add(
                        crop.get("species") + " (crop " + crop.get("cropId") + "): "
                                + assessment.code() + " - " + assessment.message()));
            }
        }

        List<String> loopLines = loops.stream()
                .map(loop -> "Loop " + loop.get("careLoopId") + " on " + loop.get("subjectType") + " "
                        + loop.get("subjectId") + " - " + loop.get("condition")
                        + ", status " + loop.get("status")
                        + ", next: " + loop.get("nextRequiredAction"))
                .toList();

        List<String> gapLines = gaps.stream()
                .map(gap -> gap.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(", ")))
                .toList();

        Map<String, Object> conditions = greenhouseConditions(twin);
        String greenhouseLine = String.format(Locale.ROOT,
                "Status %s, data freshness %s, temperature %s, humidity %s. "
                        + "%d crop(s) flagged, %d care loop(s) open, %d data gap(s).",
                conditions.get("status"), conditions.get("freshness"),
                conditions.get("temperatureCelsius") == null
                        ? "unknown" : conditions.get("temperatureCelsius") + "C",
                conditions.get("humidityPercent") == null
                        ? "unknown" : conditions.get("humidityPercent") + "%",
                attentionNames.size(), loopLines.size(), gapLines.size());

        String factSheet = summaryService.buildFactSheet(
                greenhouseLine, cropLines, warningLines, loopLines, gapLines);

        BriefingSummary summary = summaryService.summarise(factSheet, edition);

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("text", summary.text());
        section.put("headline", summary.headline());
        section.put("model", summary.model());
        section.put("attribution", summary.attribution());
        section.put("unavailableReason", summary.unavailableReason());
        // The exact input the prose was written from, so any sentence in it can
        // be checked against what was actually known.
        section.put("factSheet", factSheet);
        return section;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
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

        entry.put("soil", soilEntry(profile, assignment, soilBySensor));

        entry.put("assessments", assessmentRepository.findAllByStatus(AssessmentStatus.ACTIVE).stream()
                .filter(assessment -> crop.getId().equals(assessment.getCropId()))
                .map(assessmentMapper::toResponse)
                .toList());

        entry.put("latestObservation", cropObservationService.getObservationHistory(crop.getId()).stream()
                .reduce((first, second) -> second).orElse(null));
        entry.put("latestHarvest", harvestService.getHarvestHistory(crop.getId()).stream()
                .reduce((first, second) -> second).orElse(null));

        // The thing a current-state briefing could not show: where this crop has
        // been heading. The mint declined for six days without any single day
        // looking alarming (ADR-029).
        boolean manual = profile != null && profile.isManuallyMonitored();
        @SuppressWarnings("unchecked")
        Map<String, Object> soilForTrend = (Map<String, Object>) entry.get("soil");
        Double liveIndex = soilForTrend.get("moistureIndex") instanceof Number number
                ? number.doubleValue() : null;
        CropSoilTrend trend = manual || assignment == null
                ? CropSoilTrend.unknown()
                : trendService.trendFor(
                        assignment.getSensorId(),
                        profile == null ? null : profile.getSoilDryThresholdIndex(),
                        liveIndex);
        entry.put("trend", trendEntry(trend));
        // A structured line for the summary writer. Facts in a fixed shape, not
        // sentences - the model should read the data, not another author's prose
        // (ADR-030).
        entry.put("factLine", cropFactLine(crop, profile, entry, trend, manual, windowStart));

        return entry;
    }

    private Map<String, Object> trendEntry(CropSoilTrend trend) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("direction", trend.direction().name());
        if (!trend.isKnown()) {
            entry.put("note", "Not enough history yet to describe a trend.");
            return entry;
        }
        entry.put("changePerDayIndexPoints", round(trend.changePerDay()));
        entry.put("daysObserved", trend.daysObserved());
        entry.put("earliestIndex", round(trend.earliestIndex()));
        entry.put("latestIndex", round(trend.latestIndex()));
        entry.put("sharpRiseObserved", trend.sharpRiseObserved());
        if (trend.daysUntilDryThreshold() != null) {
            entry.put("projectedDaysUntilDryThreshold", round(trend.daysUntilDryThreshold()));
            entry.put("projectionCaveat", CropSoilTrend.projectionCaveat());
        }
        return entry;
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    // What has actually been recorded against this crop, across ALL of its
    // record types.
    //
    // This used to consult actions alone and then announce "No work has ever
    // been recorded for this crop" - a claim about four tables made by looking
    // at one. Basil had a harvest and two observations, including the
    // leaf-marks discussion, and the same snapshot carried them in
    // latestHarvest and latestObservation while the fact sheet told the model
    // there was nothing. The model has no way to catch that; it can only
    // believe the sheet.
    //
    // "Work" was the wrong word too. A harvest and an observation are not work
    // performed in the Action sense, but they are certainly a record.
    private String recordedHistory(Crop crop, Instant windowStart) {
        Instant now = clock.instant();

        List<com.greenhouse.action.ActionResponse> actions =
                actionService.listActions(crop.getId(), null, null);
        List<com.greenhouse.crop.HarvestResponse> harvests =
                harvestService.getHarvestHistory(crop.getId());
        List<com.greenhouse.crop.CropObservationResponse> observations =
                cropObservationService.getObservationHistory(crop.getId());

        if (actions.isEmpty() && harvests.isEmpty() && observations.isEmpty()) {
            return " Nothing has ever been recorded for this crop.";
        }

        List<String> inWindow = new ArrayList<>();
        actions.stream()
                .filter(action -> !action.performedAt().isBefore(windowStart))
                .map(action -> String.valueOf(action.type()).toLowerCase())
                .forEach(inWindow::add);
        long harvestsInWindow = harvests.stream()
                .filter(harvest -> !harvest.harvestedAt().isBefore(windowStart)).count();
        if (harvestsInWindow > 0) {
            inWindow.add(harvestsInWindow + " harvest" + (harvestsInWindow == 1 ? "" : "s"));
        }
        long observationsInWindow = observations.stream()
                .filter(observation -> !observation.observedAt().isBefore(windowStart)).count();
        if (observationsInWindow > 0) {
            inWindow.add(observationsInWindow
                    + " observation" + (observationsInWindow == 1 ? "" : "s"));
        }

        if (!inWindow.isEmpty()) {
            return " Recorded in window: " + String.join(", ", inWindow) + ".";
        }

        // Nothing in the window, but the crop is not a blank page. Say how long
        // ago each kind of record last happened, so "nothing lately" cannot be
        // mistaken for "nothing ever".
        List<String> lastSeen = new ArrayList<>();
        // Both history services return ascending, so the most recent is last.
        lastSeen.addAll(actions.stream()
                .max(java.util.Comparator.comparing(
                        com.greenhouse.action.ActionResponse::performedAt))
                .map(action -> String.valueOf(action.type()).toLowerCase()
                        + " " + daysAgo(action.performedAt(), now))
                .stream().toList());
        if (!harvests.isEmpty()) {
            lastSeen.add("harvest " + daysAgo(harvests.get(harvests.size() - 1).harvestedAt(), now));
        }
        if (!observations.isEmpty()) {
            lastSeen.add("observation "
                    + daysAgo(observations.get(observations.size() - 1).observedAt(), now));
        }
        return " Last recorded: " + String.join(", ", lastSeen) + ".";
    }

    private static String daysAgo(Instant at, Instant now) {
        long days = Duration.between(at, now).toDays();
        if (days <= 0) {
            return "today";
        }
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    @SuppressWarnings("unchecked")
    private String cropFactLine(
            Crop crop, CropMonitoringProfile profile, Map<String, Object> entry,
            CropSoilTrend trend, boolean manual, Instant windowStart
    ) {
        Map<String, Object> soil = (Map<String, Object>) entry.get("soil");
        StringBuilder line = new StringBuilder();
        line.append("- ").append(crop.getSpecies()).append(" (crop ").append(crop.getId()).append("): ");

        if (manual) {
            line.append("monitoring MANUAL - soil is deliberately not measured, condition UNKNOWN.");
        } else if (soil.get("moistureIndex") instanceof Number index) {
            line.append(String.format(Locale.ROOT, "moisture index %.0f", index.doubleValue()));
            if (profile != null) {
                line.append(String.format(Locale.ROOT, ", dry line %.0f",
                        profile.getSoilDryThresholdIndex()));
                line.append(profile.getSoilWetThresholdIndex() == null
                        ? ", no wet ceiling"
                        : String.format(Locale.ROOT, ", wet ceiling %.0f",
                                profile.getSoilWetThresholdIndex()));
                line.append(", band ").append(profile.getSoilMoistureBand());
            }
            line.append(".");
        } else {
            line.append("soil UNKNOWN (")
                    .append(String.valueOf(soil.getOrDefault("reason", "no reading")).toLowerCase()
                            .replace('_', ' '))
                    .append(") - not measured, not merely unremarkable.");
        }

        if (trend.isKnown()) {
            line.append(String.format(Locale.ROOT,
                    " Trend: %s, %.1f index points/day over %d days (%.0f to %.0f).",
                    trend.direction().name().toLowerCase(), trend.changePerDay(),
                    trend.daysObserved(), trend.earliestIndex(), trend.latestIndex()));
            if (trend.sharpRiseObserved()) {
                line.append(" A sharp rise occurred within that window (cause unobserved).");
            }
            if (trend.daysUntilDryThreshold() != null) {
                line.append(String.format(Locale.ROOT,
                        " Extrapolated %.1f days to the dry line if the rate holds.",
                        trend.daysUntilDryThreshold()));
            }
        } else if (!manual) {
            line.append(" Trend: not enough history.");
        }

        // These are AssessmentResponse RECORDS, not maps. Casting them to
        // Map compiles under erasure and throws the moment a crop actually has
        // an assessment - which is exactly the crop a briefing exists to
        // describe.
        List<AssessmentResponse> assessments =
                (List<AssessmentResponse>) entry.getOrDefault("assessments", List.of());
        if (!assessments.isEmpty()) {
            line.append(" Flagged: ").append(assessments.stream()
                    .map(assessment -> String.valueOf(assessment.code()))
                    .collect(java.util.stream.Collectors.joining(", "))).append(".");
        }

        line.append(recordedHistory(crop, windowStart));

        return line.toString();
    }

    private Map<String, Object> soilEntry(
            CropMonitoringProfile profile,
            CropSensorAssignment assignment,
            Map<String, SoilMoistureTwin> soilBySensor
    ) {
        Map<String, Object> soil = new LinkedHashMap<>();
        soil.put("soilMonitoringMode", profile == null
                ? String.valueOf(SoilMonitoringMode.SENSOR) : String.valueOf(profile.getSoilMonitoringMode()));

        // Deliberately manual. The status is still UNKNOWN - suppressing the
        // sensor assessment must never read as "this crop's soil is fine"
        // (ADR-024).
        if (profile != null && profile.isManuallyMonitored()) {
            soil.put("status", "UNKNOWN");
            soil.put("reason", "MANUAL_MONITORING");
            soil.put("note", "This crop is monitored by hand on purpose - no probe is expected. Its soil "
                    + "condition is not measured and can only be known by looking at it.");
            return soil;
        }

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
        Map<Long, CropMonitoringProfile> gapProfiles = profileService.enabledProfilesByCropId();
        cropRepository.findAll().stream()
                .filter(crop -> crop.getStatus() != CropStatus.ENDED)
                // A gap means "we tried to measure and could not". A crop that
                // was never going to be measured is not a failed measurement,
                // and listing it here trains the reader to ignore this section.
                .filter(crop -> {
                    CropMonitoringProfile profile = gapProfiles.get(crop.getId());
                    return profile == null || !profile.isManuallyMonitored();
                })
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

    public Optional<DailyBriefingSnapshot> snapshotForDay(LocalDate day, BriefingEdition edition) {
        return snapshotRepository.findFirstByGreenhouseDayAndEditionOrderByGeneratedAtDescIdDesc(day, edition);
    }

    public Optional<DailyBriefingSnapshot> snapshotForDay(LocalDate day) {
        return snapshotRepository.findFirstByGreenhouseDayOrderByGeneratedAtDescIdDesc(day);
    }
}
