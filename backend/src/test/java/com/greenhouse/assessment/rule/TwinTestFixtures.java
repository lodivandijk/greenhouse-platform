package com.greenhouse.assessment.rule;

import com.greenhouse.twin.model.DataQuality;
import com.greenhouse.twin.model.DeviceTwin;
import com.greenhouse.twin.model.EnvironmentAssessment;
import com.greenhouse.twin.model.EnvironmentState;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.AssessmentLevel;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
        EnvironmentAssessment assessment = new EnvironmentAssessment(AssessmentLevel.NORMAL, Set.of());
        return new ZoneTwin(zoneId, zoneId, environment, assessment, dataQuality, List.of(devices));
    }

    static DeviceTwin device(String deviceId, DeviceStatus status, Instant lastSeenAt) {
        return new DeviceTwin(deviceId, deviceId, "ESP32_BME280", status, lastSeenAt);
    }
}
