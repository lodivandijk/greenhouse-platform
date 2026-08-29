package com.greenhouse.observation.config;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "greenhouse.evaluation.enabled=false")
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
}
