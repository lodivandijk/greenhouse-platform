package com.greenhouse.observation;

import java.time.Instant;

public record ObservationStatus(
        String deviceId,
        Double temperatureCelsius,
        Double humidityPercent,
        Double pressureHpa,
        Instant receivedAt
) {
}
