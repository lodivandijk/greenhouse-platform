package com.greenhouse.twin.model;

import com.greenhouse.twin.status.DeviceStatus;

import java.time.Instant;

public record DeviceTwin(
        String deviceId,
        String name,
        String deviceType,
        DeviceStatus status,
        Instant lastSeenAt
) {
}
