package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceAvailabilityAssessmentRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    private static TwinProperties properties() {
        return new TwinProperties(
                "greenhouse-01",
                "Home Greenhouse",
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                new TwinProperties.EnvironmentalLimits(5.0, 35.0, 25.0, 90.0),
                List.of(new ZoneProperties("zone-main", "Main Greenhouse", List.of("device-1")))
        );
    }

    private final DeviceAvailabilityAssessmentRule rule =
            new DeviceAvailabilityAssessmentRule(properties(), new AssessmentCorrelationKeyFactory());

    private static GreenhouseTwin twinWithDevice(DeviceStatus status) {
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", 22.0, 50.0, 1012.0, FreshnessStatus.CURRENT, 10L, NOW.minusSeconds(10),
                TwinTestFixtures.device("device-1", status, NOW.minusSeconds(10)));
        return TwinTestFixtures.twin("greenhouse-01", NOW, zone);
    }

    @Test
    void online_producesNoFinding() {
        assertThat(rule.evaluate(twinWithDevice(DeviceStatus.ONLINE), NOW)).isEmpty();
    }

    @Test
    void delayed_producesNoFindingInV1() {
        assertThat(rule.evaluate(twinWithDevice(DeviceStatus.DELAYED), NOW)).isEmpty();
    }

    @Test
    void offline_raisesDeviceOffline() {
        List<AssessmentFinding> findings = rule.evaluate(twinWithDevice(DeviceStatus.OFFLINE), NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.DEVICE_OFFLINE);
        assertThat(findings.get(0).correlationKey()).isEqualTo("greenhouse-01:DEVICE:device-1:DEVICE_OFFLINE");
    }

    @Test
    void unknown_producesNoFinding() {
        assertThat(rule.evaluate(twinWithDevice(DeviceStatus.UNKNOWN), NOW)).isEmpty();
    }
}
