package com.greenhouse.careloop.outcome;

import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.execution.Execution;
import com.greenhouse.careloop.execution.ExecutionRepository;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.observation.SoilMoistureReadingEntity;
import com.greenhouse.observation.SoilMoistureReadingRepository;
import com.greenhouse.observation.assignment.CropSensorAssignment;
import com.greenhouse.observation.assignment.CropSensorAssignmentService;
import com.greenhouse.observation.calibration.MoistureIndex;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Sensor-based outcomes decide what the future evidence base says worked. The
// original implementation compared two readings taken AFTER the work, with no
// upper bound, and so measured drying rather than watering.
//
// The first test here is the one that matters: it reproduces the real shape of
// the data - probe responds immediately, then dries back - and asserts SUCCESS.
// Under the old logic it produced FAILED.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensorOutcomeEvaluationTest {

    private static final Instant WATERED_AT = Instant.parse("2026-08-31T09:00:00Z");
    private static final String SENSOR_ID = "soil-01";
    private static final long CROP_ID = 8L;

    @Mock private OutcomeRepository outcomeRepository;
    @Mock private OutcomeReviewEventRepository reviewEventRepository;
    @Mock private OutcomeEvaluationScheduleRepository scheduleRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private CommandService commandService;
    @Mock private DecisionService decisionService;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private SoilMoistureReadingRepository readingRepository;
    @Mock private CropSensorAssignmentService assignmentService;
    @Mock private SensorCalibrationService calibrationService;
    @Mock private ScopeService scopeService;

    private OutcomeService outcomeService;
    private OutcomeEvaluationSchedule schedule;

    @BeforeEach
    void setUp() {
        outcomeService = new OutcomeService(
                outcomeRepository, reviewEventRepository, scheduleRepository, executionRepository,
                commandService, decisionService, assessmentRepository, readingRepository,
                assignmentService, calibrationService, scopeService,
                Clock.fixed(WATERED_AT.plus(Duration.ofHours(13)), ZoneOffset.UTC));

        Execution execution = new Execution();
        execution.setId(1L);
        execution.setCommandId(2L);
        execution.setCareLoopId(3L);
        execution.setCompletedAt(WATERED_AT);

        Command command = new Command();
        command.setId(2L);
        command.setDecisionId(4L);

        Decision decision = new Decision();
        decision.setId(4L);
        decision.setActionType(CommandType.WATER_CROP);
        decision.setEvaluationMethod(OutcomeEvaluationMethod.SENSOR_BASED);
        decision.setParameters(Map.of("cropId", CROP_ID, "quantity", 250, "unit", "ml"));

        schedule = new OutcomeEvaluationSchedule(
                1L, 3L,
                WATERED_AT.plus(Duration.ofHours(2)),
                WATERED_AT.plus(Duration.ofHours(12)),
                WATERED_AT);

        when(scheduleRepository.findAllByCompletedAtIsNullAndEvaluateAfterBefore(any()))
                .thenReturn(List.of(schedule));
        when(executionRepository.findById(1L)).thenReturn(Optional.of(execution));
        when(commandService.requireCommand(2L)).thenReturn(command);
        when(decisionService.requireDecision(4L)).thenReturn(decision);

        CropSensorAssignment assignment = new CropSensorAssignment();
        assignment.setCropId(CROP_ID);
        assignment.setSensorId(SENSOR_ID);
        when(assignmentService.findCurrentAssignmentForCrop(CROP_ID)).thenReturn(Optional.of(assignment));

        SensorCalibration calibration = new SensorCalibration();
        calibration.setId(77L);
        calibration.setSensorId(SENSOR_ID);
        calibration.setVersion(1);
        calibration.setDryReferenceRaw(2814);
        calibration.setWetReferenceRaw(1181);
        when(calibrationService.findCurrentCalibration(SENSOR_ID)).thenReturn(Optional.of(calibration));

        // Index rises as raw ADC falls; the exact curve does not matter here,
        // only that it is monotonic and deterministic.
        when(calibrationService.calculateIndex(any(), any(Integer.class))).thenAnswer(invocation -> {
            int raw = invocation.getArgument(1);
            double value = 100.0 * (2814 - raw) / (2814.0 - 1181.0);
            return new MoistureIndex(Math.max(0, Math.min(100, value)), raw, 77L, 1);
        });

        when(outcomeRepository.save(any(Outcome.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SoilMoistureReadingEntity reading(int rawAdc, Instant at) {
        SoilMoistureReadingEntity entity = new SoilMoistureReadingEntity();
        entity.setSensorId(SENSOR_ID);
        entity.setRawAdc(rawAdc);
        entity.setReceivedAt(at);
        return entity;
    }

    private void baseline(SoilMoistureReadingEntity entity) {
        when(readingRepository.findFirstBySensorIdAndReceivedAtLessThanEqualOrderByReceivedAtDesc(
                eq(SENSOR_ID), eq(WATERED_AT))).thenReturn(Optional.ofNullable(entity));
    }

    private void windowReadings(List<SoilMoistureReadingEntity> readings) {
        when(readingRepository.findAllBySensorIdAndReceivedAtBetweenOrderByReceivedAtAsc(
                eq(SENSOR_ID), eq(schedule.getEvaluateAfter()), eq(schedule.getWindowEnd())))
                .thenReturn(readings);
    }

    private Outcome evaluate() {
        List<Outcome> outcomes = outcomeService.evaluateDueOutcomes();
        assertThat(outcomes).hasSize(1);
        ArgumentCaptor<Outcome> captor = ArgumentCaptor.forClass(Outcome.class);
        org.mockito.Mockito.verify(outcomeRepository).save(captor.capture());
        return captor.getValue();
    }

    // THE regression: dry soil is watered, the probe reads wet within minutes,
    // then dries back over twelve hours. This is what actually happens, and the
    // old code called it FAILED.
    @Test
    void wateringThatWorkedIsRecordedAsSuccessEvenThoughTheSoilDriesBackAfterwards() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        windowReadings(List.of(
                reading(1400, WATERED_AT.plus(Duration.ofHours(2))),
                reading(1700, WATERED_AT.plus(Duration.ofHours(6))),
                reading(2100, WATERED_AT.plus(Duration.ofHours(11)))
        ));

        Outcome outcome = evaluate();

        assertThat(outcome.getResult()).isEqualTo(OutcomeResult.SUCCESS);
        assertThat(outcome.getEvidence()).containsKey("baselineMoistureIndex");
        assertThat((Double) outcome.getEvidence().get("moistureIndexChange")).isPositive();
        assertThat(outcome.getSummary()).contains("rose from");
    }

    @Test
    void wateringThatNeverReachedTheSoilIsRecordedAsFailed() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        // Drier throughout: the water went somewhere else.
        windowReadings(List.of(
                reading(2760, WATERED_AT.plus(Duration.ofHours(2))),
                reading(2790, WATERED_AT.plus(Duration.ofHours(8)))
        ));

        assertThat(evaluate().getResult()).isEqualTo(OutcomeResult.FAILED);
    }

    @Test
    void aChangeWithinProbeNoiseIsPartialRatherThanSuccess() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        windowReadings(List.of(reading(2699, WATERED_AT.plus(Duration.ofHours(3)))));

        assertThat(evaluate().getResult()).isEqualTo(OutcomeResult.PARTIAL);
    }

    // Without a "before", nothing can honestly be attributed to the work.
    @Test
    void noBaselineReadingIsInconclusiveRatherThanGuessed() {
        baseline(null);
        windowReadings(List.of(reading(1400, WATERED_AT.plus(Duration.ofHours(2)))));

        Outcome outcome = evaluate();

        assertThat(outcome.getResult()).isEqualTo(OutcomeResult.INCONCLUSIVE);
        assertThat(outcome.getEvidence()).containsEntry("reason", "NO_BASELINE_READING");
    }

    @Test
    void noReadingsInsideTheWindowIsInconclusive() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        windowReadings(List.of());

        Outcome outcome = evaluate();

        assertThat(outcome.getResult()).isEqualTo(OutcomeResult.INCONCLUSIVE);
        assertThat(outcome.getEvidence()).containsEntry("reason", "NO_READINGS_IN_EVALUATION_WINDOW");
    }

    // The stored window is now the window that was actually queried, rather
    // than a decorative field.
    @Test
    void onlyReadingsInsideTheStoredWindowAreConsidered() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        windowReadings(List.of(reading(1400, WATERED_AT.plus(Duration.ofHours(3)))));

        Outcome outcome = evaluate();

        assertThat(outcome.getEvaluationWindowStart()).isEqualTo(schedule.getEvaluateAfter());
        assertThat(outcome.getEvaluationWindowEnd()).isEqualTo(schedule.getWindowEnd());
        assertThat(outcome.getEvidence()).containsEntry("readingsConsidered", 1);
        org.mockito.Mockito.verify(readingRepository)
                .findAllBySensorIdAndReceivedAtBetweenOrderByReceivedAtAsc(
                        SENSOR_ID, schedule.getEvaluateAfter(), schedule.getWindowEnd());
    }

    // A recalibration later must not silently change what this outcome appears
    // to have measured.
    @Test
    void theCalibrationUsedIsRecordedInTheEvidence() {
        baseline(reading(2700, WATERED_AT.minus(Duration.ofMinutes(1))));
        windowReadings(List.of(reading(1400, WATERED_AT.plus(Duration.ofHours(2)))));

        Outcome outcome = evaluate();

        assertThat(outcome.getEvidence()).containsEntry("calibrationId", 77L);
        assertThat(outcome.getEvidence()).containsEntry("calibrationVersion", 1);
    }
}
