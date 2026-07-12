package com.greenhouse.device;

import java.time.Instant;

public record DeviceStatus(
        String deviceId,
        String softwareVersion,
        String ipAddress,
        Integer signalStrengthDbm,
        Long uptimeSeconds,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long heartbeatCount,
        boolean online
) {
}
