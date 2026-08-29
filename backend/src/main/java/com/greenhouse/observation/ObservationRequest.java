package com.greenhouse.observation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

// soilMoisture is optional so payloads from firmware that predates the soil
// sensors remain valid (see docs/architecture/soil-moisture-sensor-integration-v1-spec.md).
public record ObservationRequest(
        @NotBlank String deviceId,
        Double temperatureCelsius,
        @DecimalMin("0.0") @DecimalMax("100.0") Double humidityPercent,
        Double pressureHpa,
        @Valid List<SoilMoistureReadingRequest> soilMoisture
) {
    public ObservationRequest(String deviceId, Double temperatureCelsius, Double humidityPercent, Double pressureHpa) {
        this(deviceId, temperatureCelsius, humidityPercent, pressureHpa, null);
    }

    public List<SoilMoistureReadingRequest> soilMoistureOrEmpty() {
        return soilMoisture == null ? List.of() : soilMoisture;
    }
}
