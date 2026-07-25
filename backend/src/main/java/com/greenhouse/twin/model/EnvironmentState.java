package com.greenhouse.twin.model;

public record EnvironmentState(
        Double temperatureCelsius,
        Double humidityPercent,
        Double pressureHpa
) {
}
