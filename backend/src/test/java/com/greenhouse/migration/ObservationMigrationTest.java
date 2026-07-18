package com.greenhouse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ObservationMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void observationTableHasExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class,
                "observation"
        );

        assertThat(columns).contains(
                "device_id",
                "temperature_celsius",
                "humidity_percent",
                "pressure_hpa",
                "received_at"
        );
    }

    @Test
    void flywayMigrationV1AppliedSuccessfully() {
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                Integer.class,
                "1"
        );

        assertThat(successCount).isEqualTo(1);
    }
}
