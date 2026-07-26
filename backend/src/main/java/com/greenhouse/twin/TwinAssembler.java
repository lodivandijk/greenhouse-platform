package com.greenhouse.twin;

import com.greenhouse.observation.ObservationStatus;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.DataQuality;
import com.greenhouse.twin.model.DeviceTwin;
import com.greenhouse.twin.model.EnvironmentState;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;
import com.greenhouse.twin.status.TwinStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class TwinAssembler {

    private static final String DEFAULT_DEVICE_TYPE = "ESP32_BME280";

    private static final Logger LOGGER = LoggerFactory.getLogger(TwinAssembler.class);

    public GreenhouseTwin assemble(
            TwinProperties properties,
            Map<String, Optional<ObservationStatus>> latestObservations,
            Instant generatedAt
    ) {
        LOGGER.debug("Assembling greenhouse twin for {} zone(s)", properties.zones().size());

        List<ZoneTwin> zones = properties.zones().stream()
                .map(zoneProperties -> assembleZone(
                        zoneProperties,
                        latestObservations,
                        generatedAt,
                        properties.currentThreshold(),
                        properties.offlineThreshold()
                ))
                .toList();

        Instant lastUpdatedAt = zones.stream()
                .map(ZoneTwin::dataQuality)
                .map(DataQuality::observedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        TwinStatus status = determineTwinStatus(zones);

        return new GreenhouseTwin(
                properties.greenhouseId(),
                properties.greenhouseName(),
                status,
                generatedAt,
                lastUpdatedAt,
                zones
        );
    }

    private ZoneTwin assembleZone(
            ZoneProperties zoneProperties,
            Map<String, Optional<ObservationStatus>> latestObservations,
            Instant now,
            Duration currentThreshold,
            Duration offlineThreshold
    ) {
        List<DeviceTwin> devices = zoneProperties.deviceIds().stream()
                .map(deviceId -> buildDeviceTwin(
                        deviceId,
                        latestObservations.getOrDefault(deviceId, Optional.empty()),
                        now,
                        currentThreshold,
                        offlineThreshold
                ))
                .toList();

        Optional<ObservationStatus> selected = zoneProperties.deviceIds().stream()
                .map(deviceId -> latestObservations.getOrDefault(deviceId, Optional.empty()))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(ObservationStatus::receivedAt));

        EnvironmentState environment = selected
                .map(obs -> new EnvironmentState(
                        obs.temperatureCelsius(),
                        obs.humidityPercent(),
                        obs.pressureHpa()
                ))
                .orElse(new EnvironmentState(null, null, null));

        DataQuality dataQuality = buildDataQuality(selected, now, currentThreshold, offlineThreshold);

        return new ZoneTwin(
                zoneProperties.zoneId(),
                zoneProperties.name(),
                environment,
                dataQuality,
                devices
        );
    }

    private DeviceTwin buildDeviceTwin(
            String deviceId,
            Optional<ObservationStatus> observation,
            Instant now,
            Duration currentThreshold,
            Duration offlineThreshold
    ) {
        Instant lastSeenAt = observation.map(ObservationStatus::receivedAt).orElse(null);
        DeviceStatus status = determineDeviceStatus(lastSeenAt, now, currentThreshold, offlineThreshold);

        return new DeviceTwin(deviceId, deviceId, DEFAULT_DEVICE_TYPE, status, lastSeenAt);
    }

    private DeviceStatus determineDeviceStatus(
            Instant lastSeenAt,
            Instant now,
            Duration currentThreshold,
            Duration offlineThreshold
    ) {
        if (lastSeenAt == null) {
            return DeviceStatus.UNKNOWN;
        }

        Duration age = Duration.between(lastSeenAt, now);
        if (age.compareTo(currentThreshold) < 0) {
            return DeviceStatus.ONLINE;
        }
        if (age.compareTo(offlineThreshold) < 0) {
            return DeviceStatus.DELAYED;
        }
        return DeviceStatus.OFFLINE;
    }

    private FreshnessStatus determineFreshnessStatus(
            Instant observedAt,
            Instant now,
            Duration currentThreshold,
            Duration offlineThreshold
    ) {
        if (observedAt == null) {
            return FreshnessStatus.UNKNOWN;
        }

        Duration age = Duration.between(observedAt, now);
        if (age.compareTo(currentThreshold) < 0) {
            return FreshnessStatus.CURRENT;
        }
        if (age.compareTo(offlineThreshold) < 0) {
            return FreshnessStatus.DELAYED;
        }
        return FreshnessStatus.STALE;
    }

    private DataQuality buildDataQuality(
            Optional<ObservationStatus> selected,
            Instant now,
            Duration currentThreshold,
            Duration offlineThreshold
    ) {
        if (selected.isEmpty()) {
            return new DataQuality(FreshnessStatus.UNKNOWN, null, null, false);
        }

        ObservationStatus observation = selected.get();
        Instant observedAt = observation.receivedAt();

        Duration rawAge = Duration.between(observedAt, now);
        if (rawAge.isNegative()) {
            LOGGER.warn(
                    "Observation timestamp {} for device {} is in the future relative to {}",
                    observedAt, observation.deviceId(), now
            );
        }
        long ageSeconds = Math.max(0, rawAge.getSeconds());

        FreshnessStatus freshness = determineFreshnessStatus(observedAt, now, currentThreshold, offlineThreshold);
        boolean complete = observation.temperatureCelsius() != null
                && observation.humidityPercent() != null
                && observation.pressureHpa() != null;

        return new DataQuality(freshness, ageSeconds, observedAt, complete);
    }

    private TwinStatus determineTwinStatus(List<ZoneTwin> zones) {
        List<DeviceTwin> allDevices = zones.stream()
                .flatMap(zone -> zone.devices().stream())
                .toList();

        boolean anyObservationEverExists = allDevices.stream()
                .anyMatch(device -> device.lastSeenAt() != null);
        if (!anyObservationEverExists) {
            return TwinStatus.UNKNOWN;
        }

        boolean allOfflineOrUnknown = allDevices.stream()
                .allMatch(device -> device.status() == DeviceStatus.OFFLINE || device.status() == DeviceStatus.UNKNOWN);
        if (allOfflineOrUnknown) {
            return TwinStatus.OFFLINE;
        }

        boolean anyOnlineOrDelayed = allDevices.stream()
                .anyMatch(device -> device.status() == DeviceStatus.ONLINE || device.status() == DeviceStatus.DELAYED);
        if (anyOnlineOrDelayed) {
            return TwinStatus.NORMAL;
        }

        return TwinStatus.UNKNOWN;
    }
}
