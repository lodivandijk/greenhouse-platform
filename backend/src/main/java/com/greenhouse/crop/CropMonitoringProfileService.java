package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CropMonitoringProfileService {

    private final CropMonitoringProfileRepository profileRepository;
    private final CropRepository cropRepository;
    private final Clock clock;

    public CropMonitoringProfileService(
            CropMonitoringProfileRepository profileRepository,
            CropRepository cropRepository,
            Clock clock
    ) {
        this.profileRepository = profileRepository;
        this.cropRepository = cropRepository;
        this.clock = clock;
    }

    public Optional<CropMonitoringProfile> findEnabledProfile(Long cropId) {
        return profileRepository.findByCropIdAndEnabledTrue(cropId);
    }

    public List<CropMonitoringProfile> listEnabledProfiles() {
        return profileRepository.findAllByEnabledTrue();
    }

    public Map<Long, CropMonitoringProfile> enabledProfilesByCropId() {
        return profileRepository.findAllByEnabledTrue().stream()
                .collect(Collectors.toMap(CropMonitoringProfile::getCropId, Function.identity()));
    }

    public List<CropMonitoringProfile> listVersionHistory(Long cropId) {
        return profileRepository.findAllByCropIdOrderByVersionDesc(cropId);
    }

    // Creating a profile version disables the previous one rather than editing
    // it, so an assessment raised last week still points at the thresholds that
    // actually produced it (ADR-021).
    public CropMonitoringProfile createVersion(
            Long cropId,
            double preferredTemperatureMinCelsius,
            double preferredTemperatureMaxCelsius,
            long temperatureExcursionSeconds,
            long temperatureRecoverySeconds,
            SoilMoistureStrategy soilMoistureStrategy,
            Double soilDryThresholdIndex,
            Double soilWetThresholdIndex,
            SoilMoistureBand soilMoistureBand,
            SoilMonitoringMode soilMonitoringMode,
            String createdBy,
            String sourceNotes
    ) {
        if (cropId == null || !cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }
        if (preferredTemperatureMinCelsius >= preferredTemperatureMaxCelsius) {
            throw new DomainValidationException(
                    "preferredTemperatureMinCelsius must be below preferredTemperatureMaxCelsius.");
        }
        if (temperatureExcursionSeconds <= 0 || temperatureRecoverySeconds <= 0) {
            throw new DomainValidationException(
                    "temperatureExcursionSeconds and temperatureRecoverySeconds must be positive.");
        }
        if (soilMoistureStrategy == null) {
            throw new DomainValidationException("soilMoistureStrategy is required.");
        }
        validateThreshold("soilDryThresholdIndex", soilDryThresholdIndex);
        validateThreshold("soilWetThresholdIndex", soilWetThresholdIndex);
        if (soilDryThresholdIndex != null && soilWetThresholdIndex != null
                && soilDryThresholdIndex >= soilWetThresholdIndex) {
            throw new DomainValidationException(
                    "soilDryThresholdIndex must be below soilWetThresholdIndex.");
        }

        Optional<CropMonitoringProfile> previous = profileRepository.findByCropIdAndEnabledTrue(cropId);

        CropMonitoringProfile profile = new CropMonitoringProfile();
        profile.setCropId(cropId);
        profile.setVersion(previous.map(p -> p.getVersion() + 1).orElse(1));
        profile.setPreferredTemperatureMinCelsius(preferredTemperatureMinCelsius);
        profile.setPreferredTemperatureMaxCelsius(preferredTemperatureMaxCelsius);
        profile.setTemperatureExcursionSeconds(temperatureExcursionSeconds);
        profile.setTemperatureRecoverySeconds(temperatureRecoverySeconds);
        profile.setSoilMoistureStrategy(soilMoistureStrategy);
        profile.setSoilDryThresholdIndex(soilDryThresholdIndex);
        profile.setSoilWetThresholdIndex(soilWetThresholdIndex);
        profile.setSoilMoistureBand(soilMoistureBand);
        profile.setSoilMonitoringMode(
                soilMonitoringMode == null ? SoilMonitoringMode.SENSOR : soilMonitoringMode);
        profile.setEnabled(true);
        profile.setCreatedAt(clock.instant());
        profile.setCreatedBy(createdBy == null || createdBy.isBlank() ? "system" : createdBy);
        profile.setSourceNotes(sourceNotes);
        profile.setSupersedesProfileId(previous.map(CropMonitoringProfile::getId).orElse(null));

        previous.ifPresent(p -> {
            p.setEnabled(false);
            profileRepository.save(p);
        });

        return profileRepository.save(profile);
    }

    // Changing only the monitoring mode. Everything else is carried forward from
    // the current version, so opting a crop out of sensor assessment cannot
    // silently reset its temperature range or thresholds.
    public CropMonitoringProfile changeSoilMonitoringMode(
            Long cropId,
            SoilMonitoringMode mode,
            String rationale,
            String actorId
    ) {
        if (mode == null) {
            throw new DomainValidationException("mode is required: SENSOR or MANUAL.");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new DomainValidationException(
                    "rationale is required - the reason a crop stops being sensor-assessed must be recorded, "
                            + "not inferred later from the fact that it was.");
        }
        if (cropId == null || !cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }

        CropMonitoringProfile current = profileRepository.findByCropIdAndEnabledTrue(cropId)
                .orElseThrow(() -> new DomainValidationException(
                        "Crop " + cropId + " has no enabled monitoring profile, so there is no mode to change."));

        if (current.getSoilMonitoringMode() == mode) {
            // A version whose only change is "no change" would be noise in the
            // history and would misrepresent when the decision was actually made.
            return current;
        }

        return createVersion(
                cropId,
                current.getPreferredTemperatureMinCelsius(),
                current.getPreferredTemperatureMaxCelsius(),
                current.getTemperatureExcursionSeconds(),
                current.getTemperatureRecoverySeconds(),
                current.getSoilMoistureStrategy(),
                current.getSoilDryThresholdIndex(),
                current.getSoilWetThresholdIndex(),
                current.getSoilMoistureBand(),
                mode,
                actorId == null || actorId.isBlank() ? "agent" : actorId,
                rationale
        );
    }

    // The one way soil thresholds change.
    //
    // Set per crop, because two mint plants in different corners of the same
    // greenhouse dry at different rates and the one by the door is the one that
    // wilts. Set as a BAND rather than as numbers, because a hand-picked number
    // is one nobody can justify later - the band records the judgement ("this
    // is a thirsty plant"), and the numbers follow from it.
    //
    // The resolved numbers are stored on the version alongside the band, so the
    // assessment rule keeps reading plain thresholds and a historical
    // assessment still shows the exact values that produced it - even after the
    // band's definition is later changed.
    public CropMonitoringProfile changeSoilMoistureBand(
            Long cropId,
            SoilMoistureBand band,
            String rationale,
            String actorId
    ) {
        if (band == null) {
            throw new DomainValidationException("band is required: HIGH, MEDIUM or LOW.");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new DomainValidationException(
                    "rationale is required - how thirsty a plant is judged to be is a judgement, and the "
                            + "reason for it must be recorded rather than reconstructed later from the number.");
        }
        if (cropId == null || !cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }

        CropMonitoringProfile current = profileRepository.findByCropIdAndEnabledTrue(cropId)
                .orElseThrow(() -> new DomainValidationException(
                        "Crop " + cropId + " has no enabled monitoring profile, so there is no band to set."));

        if (current.getSoilMoistureBand() == band) {
            // A version whose only change is "no change" would be noise in the
            // history and would misdate the decision.
            return current;
        }

        return createVersion(
                cropId,
                current.getPreferredTemperatureMinCelsius(),
                current.getPreferredTemperatureMaxCelsius(),
                current.getTemperatureExcursionSeconds(),
                current.getTemperatureRecoverySeconds(),
                current.getSoilMoistureStrategy(),
                band.dryThresholdIndex(),
                band.wetThresholdIndex(),
                band,
                // Carried forward: changing how thirsty a plant is judged to be
                // must never silently switch it into or out of sensor
                // assessment (ADR-024).
                current.getSoilMonitoringMode(),
                actorId == null || actorId.isBlank() ? "agent" : actorId,
                rationale
        );
    }

    private void validateThreshold(String field, Double value) {
        if (value != null && (value < 0.0 || value > 100.0)) {
            throw new DomainValidationException(field + " must be between 0 and 100 when provided.");
        }
    }
}
