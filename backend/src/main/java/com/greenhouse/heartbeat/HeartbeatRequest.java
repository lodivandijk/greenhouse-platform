package com.greenhouse.heartbeat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record HeartbeatRequest(
        @NotBlank String deviceId,
        String softwareVersion,
        String ipAddress,
        @Min(-120) @Max(0) Integer signalStrengthDbm,
        @PositiveOrZero Long uptimeSeconds
) {
}
