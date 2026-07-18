package com.greenhouse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeviceMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deviceTableHasExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class,
                "device"
        );

        assertThat(columns).contains(
                "device_id",
                "software_version",
                "first_seen_at",
                "last_seen_at",
                "last_ip_address",
                "last_signal_strength_dbm",
                "last_uptime_seconds",
                "heartbeat_count",
                "enabled",
                "updated_at"
        );
    }

    @Test
    void flywayMigrationV2AppliedSuccessfully() {
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                Integer.class,
                "2"
        );

        assertThat(successCount).isEqualTo(1);
    }
}
