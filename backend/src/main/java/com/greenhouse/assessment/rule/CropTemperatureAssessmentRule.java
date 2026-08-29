package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.assessment.AssessmentScopeType;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.assessment.reconciliation.AssessmentCorrelationKeyFactory;
import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropMonitoringProfile;
import com.greenhouse.crop.CropMonitoringProfileService;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropStatus;
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

// Interprets the ONE shared greenhouse temperature fact against EACH crop's own
// preferred range. There is no per-crop thermometer and this rule does not
// pretend otherwise - it reads the same zone temperature the zone-level rule
// reads, and produces a different verdict per crop because the crops differ,
// not because the measurement does.
//
// These are preferred GROWING ranges, not damage thresholds: severity is
// ADVISORY, the code says PREFERRED rather than LIMIT, and whether an excursion
// has lasted long enough to be worth acting on is decided by
// CareLoopCorrelationService, not here.
@Component
public class CropTemperatureAssessmentRule implements AssessmentRule {

    private static final String RULE_ID = "crop-temperature-preferred-range";
    private static final int RULE_VERSION = 1;

    private final CropRepository cropRepository;
    private final CropMonitoringProfileService profileService;
    private final TwinProperties twinProperties;
    private final AssessmentCorrelationKeyFactory correlationKeyFactory;

    public CropTemperatureAssessmentRule(
            CropRepository cropRepository,
            CropMonitoringProfileService profileService,
            TwinProperties twinProperties,
            AssessmentCorrelationKeyFactory correlationKeyFactory
    ) {
        this.cropRepository = cropRepository;
        this.profileService = profileService;
        this.twinProperties = twinProperties;
        this.correlationKeyFactory = correlationKeyFactory;
    }

    @Override
    public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
        List<Crop> activeCrops = activeCrops();
        if (activeCrops.isEmpty()) {
            return List.of();
        }

        ZoneTwin zone = primaryUsableZone(twin);
        if (zone == null) {
            return List.of();
        }

        Double temperature = zone.environment().temperatureCelsius();
        if (temperature == null) {
            return List.of();
        }

        Map<Long, CropMonitoringProfile> profiles = profileService.enabledProfilesByCropId();
        List<AssessmentFinding> findings = new ArrayList<>();

        for (Crop crop : activeCrops) {
            CropMonitoringProfile profile = profiles.get(crop.getId());
            if (profile == null) {
                // Without a profile there is no preferred range to compare
                // against; silence is correct rather than inventing thresholds.
                continue;
            }

            AssessmentCode code;
            if (temperature < profile.getPreferredTemperatureMinCelsius()) {
                code = AssessmentCode.CROP_TEMPERATURE_BELOW_PREFERRED;
            } else if (temperature > profile.getPreferredTemperatureMaxCelsius()) {
                code = AssessmentCode.CROP_TEMPERATURE_ABOVE_PREFERRED;
            } else {
                continue;
            }

            findings.add(buildFinding(crop, profile, zone, temperature, code));
        }

        return findings;
    }

    private List<Crop> activeCrops() {
        return cropRepository.findAll().stream()
                .filter(crop -> crop.getStatus() != CropStatus.ENDED)
                .toList();
    }

    private ZoneTwin primaryUsableZone(GreenhouseTwin twin) {
        return twin.zones().stream()
                .filter(zone -> {
                    FreshnessStatus freshness = zone.dataQuality().freshness();
                    return freshness == FreshnessStatus.CURRENT || freshness == FreshnessStatus.DELAYED;
                })
                .findFirst()
                .orElse(null);
    }

    private AssessmentFinding buildFinding(
            Crop crop,
            CropMonitoringProfile profile,
            ZoneTwin zone,
            double temperature,
            AssessmentCode code
    ) {
        String direction = code == AssessmentCode.CROP_TEMPERATURE_ABOVE_PREFERRED ? "above" : "below";
        String boundary = code == AssessmentCode.CROP_TEMPERATURE_ABOVE_PREFERRED
                ? String.format(Locale.ROOT, "%.1f°C", profile.getPreferredTemperatureMaxCelsius())
                : String.format(Locale.ROOT, "%.1f°C", profile.getPreferredTemperatureMinCelsius());

        String message = String.format(
                Locale.ROOT,
                "%s (crop %d) is experiencing %.1f°C, %s its preferred %s of %s.",
                crop.getSpecies(), crop.getId(), temperature, direction,
                code == AssessmentCode.CROP_TEMPERATURE_ABOVE_PREFERRED ? "maximum" : "minimum",
                boundary
        );

        Map<String, Object> evidence = Map.of(
                "actualTemperatureCelsius", temperature,
                "preferredMinimumCelsius", profile.getPreferredTemperatureMinCelsius(),
                "preferredMaximumCelsius", profile.getPreferredTemperatureMaxCelsius(),
                "observationReceivedAt", String.valueOf(zone.dataQuality().observedAt()),
                "observationAgeSeconds", zone.dataQuality().ageSeconds() == null ? 0L : zone.dataQuality().ageSeconds(),
                "sharedZoneId", zone.zoneId(),
                "note", "Preferred growing range, not a damage threshold."
        );

        String correlationKey = correlationKeyFactory.create(
                twinProperties.greenhouseId(), AssessmentScopeType.CROP, String.valueOf(crop.getId()), code
        );

        return new AssessmentFinding(
                code,
                AssessmentSeverity.ADVISORY,
                AssessmentScopeType.CROP,
                String.valueOf(crop.getId()),
                twinProperties.greenhouseId(),
                zone.zoneId(),
                null,
                message,
                evidence,
                RULE_ID,
                RULE_VERSION,
                correlationKey,
                crop.getId(),
                profile.getId(),
                profile.getVersion(),
                null,
                null
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
