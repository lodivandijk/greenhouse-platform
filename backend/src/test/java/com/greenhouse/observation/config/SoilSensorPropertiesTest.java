package com.greenhouse.observation.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoilSensorPropertiesTest {

    @Test
    void rejectsDuplicateSensorIdsAcrossAssignments() {
        assertThatThrownBy(() -> new SoilSensorProperties(List.of(
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Basil", null, null),
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Thyme", null, null)
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAnAssignmentWithNoCalibrationYet() {
        SoilSensorProperties.SoilSensorAssignment assignment =
                new SoilSensorProperties.SoilSensorAssignment("soil-06", "Basil", null, null);

        assertThat(assignment.dryRawAdc()).isNull();
        assertThat(assignment.wetRawAdc()).isNull();
    }

    @Test
    void acceptsCalibrationWhereWetIsLowerThanDry() {
        SoilSensorProperties.SoilSensorAssignment assignment =
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Basil", 2814, 1181);

        assertThat(assignment.dryRawAdc()).isEqualTo(2814);
        assertThat(assignment.wetRawAdc()).isEqualTo(1181);
    }

    @Test
    void rejectsCalibrationWhereWetIsNotLowerThanDry() {
        assertThatThrownBy(() ->
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Basil", 1500, 1500)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SoilSensorProperties.SoilSensorAssignment("soil-01", "Basil", 1500, 1600)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
