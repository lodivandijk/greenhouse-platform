package com.greenhouse.observation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SoilMoistureReadingRequest(
        @NotBlank String sensorId,
        @NotNull @Min(0) @Max(4095) Integer rawAdc
) {
}
