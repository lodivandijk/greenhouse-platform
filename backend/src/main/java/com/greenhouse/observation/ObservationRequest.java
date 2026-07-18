package com.greenhouse.observation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record ObservationRequest(
        @NotBlank String deviceId,
        Double temperatureCelsius,
        @DecimalMin("0.0") @DecimalMax("100.0") Double humidityPercent,
        Double pressureHpa
) {
}
