package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.DeviceTwin;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeviceAvailabilityAssessmentRule implements AssessmentRule {

    private static final String RULE_ID = "device-availability";
    private static final int RULE_VERSION = 1;

    private final TwinProperties twinProperties;
    private final AssessmentCorrelationKeyFactory correlationKeyFactory;

    public DeviceAvailabilityAssessmentRule(
            TwinProperties twinProperties,
            AssessmentCorrelationKeyFactory correlationKeyFactory
    ) {
        this.twinProperties = twinProperties;
        this.correlationKeyFactory = correlationKeyFactory;
    }

    @Override
    public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
        List<AssessmentFinding> findings = new ArrayList<>();

        for (ZoneTwin zone : twin.zones()) {
            for (DeviceTwin device : zone.devices()) {
                if (device.status() != DeviceStatus.OFFLINE) {
                    continue;
                }
                findings.add(buildFinding(zone, device));
            }
        }

        return findings;
    }

    private AssessmentFinding buildFinding(ZoneTwin zone, DeviceTwin device) {
        String message = String.format(
                Locale.ROOT,
                "Device %s in zone %s is offline.",
                device.deviceId(), zone.zoneId()
        );

        Map<String, Object> evidence = Map.of(
                "lastSeenAt", String.valueOf(device.lastSeenAt())
        );

        String correlationKey = correlationKeyFactory.create(
                twinProperties.greenhouseId(), AssessmentScopeType.DEVICE, device.deviceId(), AssessmentCode.DEVICE_OFFLINE
        );

        return new AssessmentFinding(
                AssessmentCode.DEVICE_OFFLINE,
                AssessmentSeverity.CRITICAL,
                AssessmentScopeType.DEVICE,
                device.deviceId(),
                twinProperties.greenhouseId(),
                zone.zoneId(),
                device.deviceId(),
                message,
                evidence,
                RULE_ID,
                RULE_VERSION,
                correlationKey
        );
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public int ruleVersion() {
        return RULE_VERSION;
    }
}
