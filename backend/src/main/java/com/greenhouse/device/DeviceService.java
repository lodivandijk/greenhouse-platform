package com.greenhouse.device;

import com.greenhouse.heartbeat.HeartbeatRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class DeviceService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2);

    private final DeviceRepository deviceRepository;
    private final Clock clock = Clock.systemUTC();

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public DeviceStatus recordHeartbeat(HeartbeatRequest request) {
        Instant receivedAt = clock.instant();

        DeviceEntity device = deviceRepository.findById(request.deviceId())
                .orElseGet(() -> new DeviceEntity(
                        request.deviceId(),
                        request.softwareVersion(),
                        receivedAt,
                        receivedAt,
                        request.ipAddress(),
                        request.signalStrengthDbm(),
                        request.uptimeSeconds(),
                        0,
                        true,
                        receivedAt
                ));

        device.setSoftwareVersion(request.softwareVersion());
        device.setLastSeenAt(receivedAt);
        device.setLastIpAddress(request.ipAddress());
        device.setLastSignalStrengthDbm(request.signalStrengthDbm());
        device.setLastUptimeSeconds(request.uptimeSeconds());
        device.setHeartbeatCount(device.getHeartbeatCount() + 1);
        device.setUpdatedAt(receivedAt);

        return toStatus(deviceRepository.save(device), receivedAt);
    }

    public List<DeviceStatus> getAllDevices() {
        Instant now = clock.instant();

        return deviceRepository.findAll().stream()
                .map(device -> toStatus(device, now))
                .sorted(Comparator.comparing(DeviceStatus::deviceId))
                .toList();
    }

    public DeviceStatus getDevice(String deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        return toStatus(device, clock.instant());
    }

    private DeviceStatus toStatus(DeviceEntity device, Instant now) {
        boolean online = Duration.between(device.getLastSeenAt(), now)
                .compareTo(ONLINE_THRESHOLD) <= 0;

        return new DeviceStatus(
                device.getDeviceId(),
                device.getSoftwareVersion(),
                device.getLastIpAddress(),
                device.getLastSignalStrengthDbm(),
                device.getLastUptimeSeconds(),
                device.getFirstSeenAt(),
                device.getLastSeenAt(),
                device.getHeartbeatCount(),
                online
        );
    }
}
