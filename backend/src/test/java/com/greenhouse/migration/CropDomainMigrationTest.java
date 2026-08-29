package com.greenhouse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
class CropDomainMigrationTest {

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
    void cropTableHasExpectedColumns() {
        assertThat(columnsOf("crop")).contains(
                "id", "species", "variety", "location_id", "planted_at", "ended_at",
                "status", "notes", "created_at", "updated_at"
        );
    }

    @Test
    void goalTableHasExpectedColumns() {
        assertThat(columnsOf("goal")).contains(
                "id", "crop_id", "goal_type", "description", "status", "priority",
                "source_instruction", "metadata_json", "created_at", "updated_at"
        );
    }

    @Test
    void harvestTableHasExpectedColumns() {
        assertThat(columnsOf("harvest")).contains(
                "id", "crop_id", "harvested_at", "quantity", "unit", "notes", "created_at"
        );
    }

    @Test
    void cropObservationTableHasExpectedColumns() {
        assertThat(columnsOf("crop_observation")).contains(
                "id", "crop_id", "metric", "value_type", "numeric_value", "text_value",
                "boolean_value", "unit", "source", "confidence", "observed_at", "notes",
                "metadata_json", "created_at"
        );
    }

    @Test
    void actionTableHasExpectedColumns() {
        assertThat(columnsOf("action")).contains(
                "id", "crop_id", "type", "description", "quantity", "unit",
                "performed_at", "performed_by", "created_at"
        );
    }

    @Test
    void flywayMigrationsV4ThroughV8AppliedSuccessfully() {
        for (String version : List.of("4", "5", "6", "7", "8")) {
            Integer successCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                    Integer.class,
                    version
            );
            assertThat(successCount).as("migration V%s applied", version).isEqualTo(1);
        }
    }
}
