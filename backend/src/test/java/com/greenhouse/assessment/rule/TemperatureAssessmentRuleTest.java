package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemperatureAssessmentRuleTest {

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

    private final TemperatureAssessmentRule rule =
            new TemperatureAssessmentRule(properties(), new AssessmentCorrelationKeyFactory());

    private static GreenhouseTwin twinWithTemperature(Double temperature, FreshnessStatus freshness, DeviceStatus deviceStatus) {
        return TwinTestFixtures.twin("greenhouse-01", NOW,
                TwinTestFixtures.zone(
                        "zone-main", temperature, 50.0, 1012.0, freshness, 10L, NOW.minusSeconds(10),
                        TwinTestFixtures.device("device-1", deviceStatus, NOW.minusSeconds(10))
                ));
    }

    @Test
    void belowMinimum_raisesTemperatureBelowLimit() {
        GreenhouseTwin twin = twinWithTemperature(4.9, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.TEMPERATURE_BELOW_LIMIT);
    }

    @Test
    void equalToMinimum_isNormal() {
        GreenhouseTwin twin = twinWithTemperature(5.0, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void insideRange_isNormal() {
        GreenhouseTwin twin = twinWithTemperature(22.0, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void equalToMaximum_isNormal() {
        GreenhouseTwin twin = twinWithTemperature(35.0, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void aboveMaximum_raisesTemperatureAboveLimit() {
        GreenhouseTwin twin = twinWithTemperature(35.1, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.TEMPERATURE_ABOVE_LIMIT);
        assertThat(findings.get(0).correlationKey()).isEqualTo("greenhouse-01:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT");
    }

    @Test
    void missingTemperature_producesNoFinding() {
        GreenhouseTwin twin = twinWithTemperature(null, FreshnessStatus.CURRENT, DeviceStatus.ONLINE);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void staleSource_producesNoFinding() {
        GreenhouseTwin twin = twinWithTemperature(40.0, FreshnessStatus.STALE, DeviceStatus.OFFLINE);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }
}
