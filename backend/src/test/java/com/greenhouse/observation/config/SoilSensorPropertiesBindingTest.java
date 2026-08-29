package com.greenhouse.observation.config;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
class SoilSensorPropertiesBindingTest {

    @Autowired
    private SoilSensorProperties soilSensorProperties;

    @Test
    void bindsTheFiveConfiguredAssignmentsFromApplicationYml() {
        assertThat(soilSensorProperties.assignments())
                .extracting(
                        SoilSensorProperties.SoilSensorAssignment::sensorId,
                        SoilSensorProperties.SoilSensorAssignment::plant
                )
                .containsExactly(
                        Tuple.tuple("soil-01", "Basil"),
                        Tuple.tuple("soil-02", "Thyme"),
                        Tuple.tuple("soil-03", "Mint"),
                        Tuple.tuple("soil-04", "Sage"),
                        Tuple.tuple("soil-05", "Oregano")
                );
    }

    @Test
    void bindsCalibrationReferenceValuesForEverySensor() {
        assertThat(soilSensorProperties.assignments())
                .extracting(
                        SoilSensorProperties.SoilSensorAssignment::sensorId,
                        SoilSensorProperties.SoilSensorAssignment::dryRawAdc,
                        SoilSensorProperties.SoilSensorAssignment::wetRawAdc
                )
                .containsExactly(
                        Tuple.tuple("soil-01", 2814, 1181),
                        Tuple.tuple("soil-02", 2706, 1121),
                        Tuple.tuple("soil-03", 2707, 1105),
                        Tuple.tuple("soil-04", 2794, 1179),
                        Tuple.tuple("soil-05", 2717, 1134)
                );
    }
}
