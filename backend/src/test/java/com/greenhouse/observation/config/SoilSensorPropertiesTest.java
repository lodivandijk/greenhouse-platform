package com.greenhouse.observation.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoilSensorPropertiesTest {

    @Test
    void rejectsDuplicateSensorIdsAcrossAssignments() {
        assertThatThrownBy(() -> new SoilSensorProperties(List.of(
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Basil"),
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Thyme")
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
