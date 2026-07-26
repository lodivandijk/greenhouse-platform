package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.FreshnessStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class HumidityAssessmentRule implements AssessmentRule {

    private static final String RULE_ID = "humidity-operating-limit";
    private static final int RULE_VERSION = 1;

    private final TwinProperties twinProperties;
    private final AssessmentCorrelationKeyFactory correlationKeyFactory;

    public HumidityAssessmentRule(
            TwinProperties twinProperties,
            AssessmentCorrelationKeyFactory correlationKeyFactory
    ) {
        this.twinProperties = twinProperties;
        this.correlationKeyFactory = correlationKeyFactory;
    }

    @Override
    public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
        List<AssessmentFinding> findings = new ArrayList<>();
        TwinProperties.EnvironmentalLimits limits = twinProperties.environmentalLimits();

        for (ZoneTwin zone : twin.zones()) {
            if (!isUsable(zone)) {
                continue;
            }

            Double humidity = zone.environment().humidityPercent();
            if (humidity == null) {
                continue;
            }

            AssessmentCode code;
            if (humidity < limits.minimumHumidityPercent()) {
                code = AssessmentCode.HUMIDITY_BELOW_LIMIT;
            } else if (humidity > limits.maximumHumidityPercent()) {
                code = AssessmentCode.HUMIDITY_ABOVE_LIMIT;
            } else {
                continue;
            }

            findings.add(buildFinding(zone, humidity, limits, code));
        }

        return findings;
    }

    private boolean isUsable(ZoneTwin zone) {
        FreshnessStatus freshness = zone.dataQuality().freshness();
        return freshness == FreshnessStatus.CURRENT || freshness == FreshnessStatus.DELAYED;
    }

    private AssessmentFinding buildFinding(
            ZoneTwin zone,
            double humidity,
            TwinProperties.EnvironmentalLimits limits,
            AssessmentCode code
    ) {
        String limitDescription = code == AssessmentCode.HUMIDITY_ABOVE_LIMIT
                ? String.format(Locale.ROOT, "above the configured maximum of %.1f%%", limits.maximumHumidityPercent())
                : String.format(Locale.ROOT, "below the configured minimum of %.1f%%", limits.minimumHumidityPercent());

        String message = String.format(
                Locale.ROOT,
                "Zone %s humidity of %.1f%% is %s.",
                zone.zoneId(), humidity, limitDescription
        );

        Map<String, Object> evidence = Map.of(
                "actualHumidityPercent", humidity,
                "minimumHumidityPercent", limits.minimumHumidityPercent(),
                "maximumHumidityPercent", limits.maximumHumidityPercent(),
                "observationReceivedAt", String.valueOf(zone.dataQuality().observedAt()),
                "observationAgeSeconds", zone.dataQuality().ageSeconds() == null ? 0L : zone.dataQuality().ageSeconds()
        );

        String correlationKey = correlationKeyFactory.create(
                twinProperties.greenhouseId(), AssessmentScopeType.ZONE, zone.zoneId(), code
        );

        return new AssessmentFinding(
                code,
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
