package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ObservationFreshnessAssessmentRule implements AssessmentRule {

    private static final String RULE_ID = "observation-freshness";
    private static final int RULE_VERSION = 1;

    private final TwinProperties twinProperties;
    private final AssessmentCorrelationKeyFactory correlationKeyFactory;

    public ObservationFreshnessAssessmentRule(
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
            if (zone.dataQuality().freshness() != FreshnessStatus.STALE) {
                continue;
            }

            // DeviceAvailabilityAssessmentRule already reports this at device scope
            // when every device in the zone is offline - avoid a redundant zone-level finding.
            boolean allDevicesOfflineOrUnknown = zone.devices().stream()
                    .allMatch(device -> device.status() == DeviceStatus.OFFLINE
                            || device.status() == DeviceStatus.UNKNOWN);
            if (allDevicesOfflineOrUnknown) {
                continue;
            }

            findings.add(buildFinding(zone));
        }

        return findings;
    }

    private AssessmentFinding buildFinding(ZoneTwin zone) {
        long ageSeconds = zone.dataQuality().ageSeconds() == null ? 0L : zone.dataQuality().ageSeconds();

        String message = String.format(
                Locale.ROOT,
                "Zone %s's latest observation is stale (%ds old).",
                zone.zoneId(), ageSeconds
        );

        Map<String, Object> evidence = Map.of(
                "observationReceivedAt", String.valueOf(zone.dataQuality().observedAt()),
                "observationAgeSeconds", ageSeconds
        );

        String correlationKey = correlationKeyFactory.create(
                twinProperties.greenhouseId(), AssessmentScopeType.ZONE, zone.zoneId(), AssessmentCode.OBSERVATION_STALE
        );

        return new AssessmentFinding(
                AssessmentCode.OBSERVATION_STALE,
                AssessmentSeverity.WARNING,
                AssessmentScopeType.ZONE,
                zone.zoneId(),
                twinProperties.greenhouseId(),
                zone.zoneId(),
                null,
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
