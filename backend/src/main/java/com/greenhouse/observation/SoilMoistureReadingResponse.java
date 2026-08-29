package com.greenhouse.observation;

import java.time.Instant;

public record SoilMoistureReadingResponse(
        Long id,
        String deviceId,
        String sensorId,
        Integer rawAdc,
        Double millivolts,
        Instant receivedAt
) {
}
