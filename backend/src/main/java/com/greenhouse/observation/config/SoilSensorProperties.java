package com.greenhouse.observation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// Keeps the soil-sensor-to-plant assignment out of ESP32 firmware entirely
// (docs/architecture/soil-moisture-sensor-integration-v2-spec.md section
// 4.2): sensorId is the durable hardware identity (see SoilSensors::ALL in
// the firmware); which plant a sensor currently monitors is Pi-side
// configuration, changeable without reflashing. See ADR-018.
@Validated
@ConfigurationProperties(prefix = "greenhouse.soil-sensors")
public record SoilSensorProperties(
        @NotEmpty @Valid List<SoilSensorAssignment> assignments
) {

    public SoilSensorProperties {
        if (assignments != null) {
            long distinctSensorIds = assignments.stream()
                    .map(SoilSensorAssignment::sensorId)
                    .distinct()
                    .count();
            if (distinctSensorIds != assignments.size()) {
                throw new IllegalArgumentException(
                        "greenhouse.soil-sensors.assignments must not repeat the same sensorId");
            }
        }
    }

    public record SoilSensorAssignment(
            @NotBlank String sensorId,
            @NotBlank String plant
    ) {
    }
}
