package com.greenhouse.assessment.rule;

import com.greenhouse.twin.model.DataQuality;
import com.greenhouse.twin.model.DeviceTwin;
import com.greenhouse.twin.model.EnvironmentState;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;

import java.time.Instant;
import java.util.List;

final class TwinTestFixtures {

    private TwinTestFixtures() {
    }

    static GreenhouseTwin twin(String greenhouseId, Instant generatedAt, ZoneTwin... zones) {
        return new GreenhouseTwin(greenhouseId, "Test Greenhouse", null, generatedAt, null, List.of(zones));
    }

    static ZoneTwin zone(
            String zoneId,
            Double temperature,
            Double humidity,
            Double pressure,
            FreshnessStatus freshness,
            Long ageSeconds,
            Instant observedAt,
            DeviceTwin... devices
    ) {
        EnvironmentState environment = new EnvironmentState(temperature, humidity, pressure);
        boolean complete = temperature != null && humidity != null && pressure != null;
        DataQuality dataQuality = new DataQuality(freshness, ageSeconds, observedAt, complete);
        return new ZoneTwin(zoneId, zoneId, environment, dataQuality, List.of(devices));
    }

    static DeviceTwin device(String deviceId, DeviceStatus status, Instant lastSeenAt) {
        return new DeviceTwin(deviceId, deviceId, "ESP32_BME280", status, lastSeenAt);
    }
}
