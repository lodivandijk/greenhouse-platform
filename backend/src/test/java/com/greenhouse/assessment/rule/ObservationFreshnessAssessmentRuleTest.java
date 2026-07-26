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

class ObservationFreshnessAssessmentRuleTest {

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

    private final ObservationFreshnessAssessmentRule rule =
            new ObservationFreshnessAssessmentRule(properties(), new AssessmentCorrelationKeyFactory());

    @Test
    void current_producesNoFinding() {
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", 22.0, 50.0, 1012.0, FreshnessStatus.CURRENT, 10L, NOW.minusSeconds(10),
                TwinTestFixtures.device("device-1", DeviceStatus.ONLINE, NOW.minusSeconds(10)));
        GreenhouseTwin twin = TwinTestFixtures.twin("greenhouse-01", NOW, zone);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void delayed_producesNoFindingInV1() {
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", 22.0, 50.0, 1012.0, FreshnessStatus.DELAYED, 150L, NOW.minusSeconds(150),
                TwinTestFixtures.device("device-1", DeviceStatus.DELAYED, NOW.minusSeconds(150)));
        GreenhouseTwin twin = TwinTestFixtures.twin("greenhouse-01", NOW, zone);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void staleWithAnOnlineDeviceInZone_raisesObservationStale() {
        // A stale zone reading where at least one device in the zone is not offline/unknown -
        // e.g. a second, currently-online device in the same zone.
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", 22.0, 50.0, 1012.0, FreshnessStatus.STALE, 400L, NOW.minusSeconds(400),
                TwinTestFixtures.device("device-1", DeviceStatus.OFFLINE, NOW.minusSeconds(400)),
                TwinTestFixtures.device("device-2", DeviceStatus.ONLINE, NOW.minusSeconds(5)));
        GreenhouseTwin twin = TwinTestFixtures.twin("greenhouse-01", NOW, zone);

        List<AssessmentFinding> findings = rule.evaluate(twin, NOW);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).code()).isEqualTo(AssessmentCode.OBSERVATION_STALE);
    }

    @Test
    void staleWithAllDevicesOffline_isSuppressedInFavorOfDeviceOfflineAssessment() {
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", 22.0, 50.0, 1012.0, FreshnessStatus.STALE, 400L, NOW.minusSeconds(400),
                TwinTestFixtures.device("device-1", DeviceStatus.OFFLINE, NOW.minusSeconds(400)));
        GreenhouseTwin twin = TwinTestFixtures.twin("greenhouse-01", NOW, zone);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }

    @Test
    void unknown_producesNoFinding() {
        ZoneTwin zone = TwinTestFixtures.zone("zone-main", null, null, null, FreshnessStatus.UNKNOWN, null, null,
                TwinTestFixtures.device("device-1", DeviceStatus.UNKNOWN, null));
        GreenhouseTwin twin = TwinTestFixtures.twin("greenhouse-01", NOW, zone);

        assertThat(rule.evaluate(twin, NOW)).isEmpty();
    }
}
