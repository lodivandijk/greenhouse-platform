package com.greenhouse.device;

import com.greenhouse.heartbeat.HeartbeatRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DeviceRegistry {

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2);

    private final ConcurrentMap<String, StoredDevice> devices = new ConcurrentHashMap<>();
    private final Clock clock;

    public DeviceRegistry() {
        this(Clock.systemUTC());
    }

    DeviceRegistry(Clock clock) {
        this.clock = clock;
    }

    public DeviceStatus recordHeartbeat(HeartbeatRequest request) {
        Instant receivedAt = clock.instant();

        StoredDevice stored = devices.compute(
                request.deviceId(),
                (deviceId, existing) -> {
                    if (existing == null) {
                        return new StoredDevice(
                                deviceId,
                                request.softwareVersion(),
                                request.ipAddress(),
                                request.signalStrengthDbm(),
                                request.uptimeSeconds(),
                                receivedAt,
                                receivedAt,
                                1
                        );
                    }

                    return new StoredDevice(
                            deviceId,
                            request.softwareVersion(),
                            request.ipAddress(),
                            request.signalStrengthDbm(),
                            request.uptimeSeconds(),
                            existing.firstSeenAt(),
                            receivedAt,
                            existing.heartbeatCount() + 1
                    );
                }
        );

        return toStatus(stored, receivedAt);
    }

    public List<DeviceStatus> getAllDevices() {
        Instant now = clock.instant();

        return devices.values().stream()
                .map(device -> toStatus(device, now))
                .sorted(Comparator.comparing(DeviceStatus::deviceId))
                .toList();
    }

    public DeviceStatus getDevice(String deviceId) {
        StoredDevice stored = devices.get(deviceId);

        if (stored == null) {
            throw new DeviceNotFoundException(deviceId);
        }

        return toStatus(stored, clock.instant());
    }

    private DeviceStatus toStatus(StoredDevice device, Instant now) {
        boolean online = Duration.between(device.lastSeenAt(), now)
                .compareTo(ONLINE_THRESHOLD) <= 0;

        return new DeviceStatus(
                device.deviceId(),
                device.softwareVersion(),
                device.ipAddress(),
                device.signalStrengthDbm(),
                device.uptimeSeconds(),
                device.firstSeenAt(),
                device.lastSeenAt(),
                device.heartbeatCount(),
                online
        );
    }

    private record StoredDevice(
            String deviceId,
            String softwareVersion,
            String ipAddress,
            Integer signalStrengthDbm,
            Long uptimeSeconds,
            Instant firstSeenAt,
            Instant lastSeenAt,
            long heartbeatCount
    ) {
    }
}
