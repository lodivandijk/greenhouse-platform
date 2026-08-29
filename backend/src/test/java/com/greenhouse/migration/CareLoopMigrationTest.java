package com.greenhouse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
class CareLoopMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class,
                table
        );
    }

    @Test
    void cropMonitoringProfileTableHasExpectedColumns() {
        assertThat(columnsOf("crop_monitoring_profile")).contains(
                "crop_id", "version", "preferred_temperature_min_celsius", "preferred_temperature_max_celsius",
                "temperature_excursion_seconds", "temperature_recovery_seconds", "soil_moisture_strategy",
                "soil_dry_threshold_index", "soil_wet_threshold_index", "enabled", "created_at", "created_by",
                "source_notes", "supersedes_profile_id"
        );
    }

    @Test
    void sensorCalibrationTableHasExpectedColumns() {
        assertThat(columnsOf("sensor_calibration")).contains(
                "sensor_id", "version", "dry_reference_raw", "wet_reference_raw", "calibrated_at",
                "calibrated_by", "method", "notes", "valid_to", "supersedes_calibration_id"
        );
    }

    @Test
    void cropSensorAssignmentTableHasExpectedColumns() {
        assertThat(columnsOf("crop_sensor_assignment")).contains(
                "sensor_id", "crop_id", "version", "assigned_at", "assigned_by", "valid_to",
                "supersedes_assignment_id"
        );
    }

    @Test
    void assessmentLifecycleEventTableHasExpectedColumns() {
        assertThat(columnsOf("assessment_lifecycle_event")).contains(
                "assessment_id", "event_type", "correlation_key", "code", "severity", "status",
                "evidence_json", "rule_id", "rule_version", "monitoring_profile_id", "calibration_id",
                "occurred_at", "actor_type"
        );
    }

    @Test
    void assessmentTableGainedCropContextColumns() {
        assertThat(columnsOf("assessment")).contains(
                "crop_id", "monitoring_profile_id", "monitoring_profile_version",
                "calibration_id", "calibration_version"
        );
    }

    @Test
    void careLoopTablesHaveExpectedColumns() {
        assertThat(columnsOf("care_loop")).contains(
                "primary_subject_type", "primary_subject_id", "condition_type", "correlation_key",
                "opened_at", "closed_at", "created_by"
        );
        assertThat(columnsOf("care_loop_assessment")).contains("care_loop_id", "assessment_id", "linked_at");
        assertThat(columnsOf("care_loop_status_event")).contains(
                "care_loop_id", "status", "reason_code", "actor_type", "occurred_at"
        );
    }

    @Test
    void loopRecordScopeEventTableHasExpectedColumns() {
        assertThat(columnsOf("loop_record_scope_event")).contains(
                "care_loop_id", "record_type", "record_id", "scope", "reason_code", "reason_text",
                "actor_type", "actor_id", "occurred_at", "request_id"
        );
    }

    @Test
    void decisionTablesHaveExpectedColumns() {
        assertThat(columnsOf("decision")).contains(
                "care_loop_id", "action_type", "parameters_json", "rationale", "expected_effect",
                "evaluation_method", "evaluation_delay_seconds", "evaluation_window_seconds",
                "success_criteria", "proposed_by", "proposed_at", "supersedes_decision_id"
        );
        assertThat(columnsOf("decision_lifecycle_event")).contains(
                "decision_id", "event_type", "actor_type", "occurred_at", "request_id"
        );
        assertThat(columnsOf("decision_assessment")).contains("decision_id", "assessment_id");
        assertThat(columnsOf("decision_goal")).contains("decision_id", "goal_id");
    }

    @Test
    void commandTablesHaveExpectedColumns() {
        assertThat(columnsOf("command")).contains(
                "care_loop_id", "decision_id", "command_type", "target_type", "target_id",
                "parameters_json", "issued_at", "expires_at", "supersedes_command_id"
        );
        assertThat(columnsOf("command_lifecycle_event")).contains(
                "command_id", "event_type", "deferred_until", "actor_type", "occurred_at"
        );
    }

    @Test
    void executionTableHasExpectedColumns() {
        assertThat(columnsOf("execution")).contains(
                "care_loop_id", "command_id", "result", "actual_parameters_json", "performed_by",
                "started_at", "completed_at", "notes", "recorded_at", "corrects_execution_id"
        );
    }

    @Test
    void outcomeTablesHaveExpectedColumns() {
        assertThat(columnsOf("outcome")).contains(
                "care_loop_id", "decision_id", "command_id", "execution_id", "result", "evaluated_at",
                "evaluation_window_start", "evaluation_window_end", "evidence_json", "summary",
                "evaluated_by", "supersedes_outcome_id"
        );
        assertThat(columnsOf("outcome_review_event")).contains(
                "outcome_id", "review_note", "disputed", "resulting_outcome_id", "actor_type", "occurred_at"
        );
        assertThat(columnsOf("outcome_evaluation_schedule")).contains(
                "execution_id", "care_loop_id", "evaluate_after", "window_end", "completed_at"
        );
    }

    @Test
    void idempotentRequestTableHasExpectedColumns() {
        assertThat(columnsOf("idempotent_request")).contains(
                "idempotency_key", "tool_name", "request_fingerprint", "status", "result_json",
                "created_at", "completed_at"
        );
    }

    @Test
    void sensorCalibrationSeededWithMeasuredValues() {
        // The real measurements from ADR-020 must survive the move into the
        // database - re-measuring five probes is not a cheap operation.
        Integer soil01Dry = jdbcTemplate.queryForObject(
                "SELECT dry_reference_raw FROM sensor_calibration WHERE sensor_id = 'soil-01' AND version = 1",
                Integer.class
        );
        Integer soil01Wet = jdbcTemplate.queryForObject(
                "SELECT wet_reference_raw FROM sensor_calibration WHERE sensor_id = 'soil-01' AND version = 1",
                Integer.class
        );

        assertThat(soil01Dry).isEqualTo(2814);
        assertThat(soil01Wet).isEqualTo(1181);

        Integer seededSensors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sensor_calibration WHERE calibrated_by = 'migration-seed'",
                Integer.class
        );
        assertThat(seededSensors).isEqualTo(5);
    }

    @Test
    void flywayMigrationsV10ThroughV26AppliedSuccessfully() {
        for (int version = 10; version <= 26; version++) {
            Integer successCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                    Integer.class,
                    String.valueOf(version)
            );
            assertThat(successCount).as("migration V%s applied", version).isEqualTo(1);
        }
    }
}
