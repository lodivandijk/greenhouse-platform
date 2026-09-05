package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Bands are how thresholds change now, so the properties that matter are: the
// numbers follow from the band, the band is recorded next to the numbers it
// produced, and changing it never disturbs anything else about the crop
// (ADR-028).
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class SoilMoistureBandTest {

    @Autowired private CropService cropService;
    @Autowired private CropRepository cropRepository;
    @Autowired private CropMonitoringProfileService profileService;
    @Autowired private CropMonitoringProfileRepository profileRepository;

    private Crop crop;

    @BeforeEach
    void createCrop() {
        Long cropId = cropService.createCrop(
                "Thyme", null, "planter-band-test", Instant.now(), "band test").id();
        crop = cropRepository.findById(cropId).orElseThrow();

        profileService.createVersion(
                crop.getId(), 15.0, 25.0, 3600L, 1800L,
                SoilMoistureStrategy.DRY_BETWEEN_WATERING,
                SoilMoistureBand.LOW.dryThresholdIndex(), SoilMoistureBand.LOW.wetThresholdIndex(),
                SoilMoistureBand.LOW, SoilMonitoringMode.SENSOR, "test", "Initial profile.");
    }

    @AfterEach
    void cleanUp() {
        profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId())
                .forEach(profileRepository::delete);
        cropRepository.findById(crop.getId()).ifPresent(cropRepository::delete);
    }

    // --- the band definitions themselves ---------------------------------

    // Every band must produce a range the profile validator will accept;
    // otherwise adding a band would fail only at the moment someone used it.
    @Test
    void everyBandDefinesAUsableRange() {
        for (SoilMoistureBand band : SoilMoistureBand.values()) {
            assertThat(band.dryThresholdIndex()).isBetween(0.0, 100.0);
            if (band.wetThresholdIndex() != null) {
                assertThat(band.wetThresholdIndex()).isBetween(0.0, 100.0);
                assertThat(band.dryThresholdIndex())
                        .as("%s dry threshold must be below its wet threshold", band)
                        .isLessThan(band.wetThresholdIndex());
            }
            assertThat(band.description()).isNotBlank();
        }
    }

    // HIGH is the thirstiest, so it must be flagged as dry soonest. If someone
    // adds VERY_HIGH later, this ordering is what they must preserve.
    @Test
    void thirstierBandsAreFlaggedAsDrySooner() {
        assertThat(SoilMoistureBand.HIGH.dryThresholdIndex())
                .isGreaterThan(SoilMoistureBand.MEDIUM.dryThresholdIndex());
        assertThat(SoilMoistureBand.MEDIUM.dryThresholdIndex())
                .isGreaterThan(SoilMoistureBand.LOW.dryThresholdIndex());
    }

    // --- changing a crop's band ------------------------------------------

    @Test
    void changingTheBandResolvesTheThresholdsAndKeepsTheOldVersion() {
        CropMonitoringProfile updated = profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.HIGH, "It wilted before the old line fired.", "lodi");

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getSoilMoistureBand()).isEqualTo(SoilMoistureBand.HIGH);
        assertThat(updated.getSoilDryThresholdIndex()).isEqualTo(50.0);
        // HIGH has no wet ceiling.
        assertThat(updated.getSoilWetThresholdIndex()).isNull();
        assertThat(updated.getCreatedBy()).isEqualTo("lodi");
        assertThat(updated.getSourceNotes()).isEqualTo("It wilted before the old line fired.");

        List<CropMonitoringProfile> history =
                profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getEnabled()).isFalse();
        assertThat(history.get(1).getSoilDryThresholdIndex()).isEqualTo(30.0);
        assertThat(updated.getSupersedesProfileId()).isEqualTo(history.get(1).getId());
    }

    // The resolved numbers are stored, not just the band, so a historical
    // assessment still shows the exact values that produced it even after the
    // band's definition is later changed in code.
    @Test
    void theResolvedThresholdsArePersistedAlongsideTheBand() {
        CropMonitoringProfile updated = profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.MEDIUM, "Somewhere in between.", "lodi");

        CropMonitoringProfile reloaded = profileRepository.findById(updated.getId()).orElseThrow();
        assertThat(reloaded.getSoilMoistureBand()).isEqualTo(SoilMoistureBand.MEDIUM);
        assertThat(reloaded.getSoilDryThresholdIndex())
                .isEqualTo(SoilMoistureBand.MEDIUM.dryThresholdIndex());
        assertThat(reloaded.getSoilWetThresholdIndex())
                .isEqualTo(SoilMoistureBand.MEDIUM.wetThresholdIndex());
    }

    @Test
    void everythingElseAboutTheCropIsCarriedForward() {
        CropMonitoringProfile before = profileService.findEnabledProfile(crop.getId()).orElseThrow();

        CropMonitoringProfile updated = profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.HIGH, "Thirstier than we thought.", "lodi");

        assertThat(updated.getSoilMoistureStrategy()).isEqualTo(before.getSoilMoistureStrategy());
        assertThat(updated.getPreferredTemperatureMinCelsius())
                .isEqualTo(before.getPreferredTemperatureMinCelsius());
        assertThat(updated.getPreferredTemperatureMaxCelsius())
                .isEqualTo(before.getPreferredTemperatureMaxCelsius());
        assertThat(updated.getTemperatureExcursionSeconds())
                .isEqualTo(before.getTemperatureExcursionSeconds());
    }

    // Changing how thirsty a plant is judged to be must never quietly switch it
    // into or out of sensor assessment.
    @Test
    void theMonitoringModeIsCarriedForward() {
        profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "Tended by hand.", "lodi");

        CropMonitoringProfile updated = profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.HIGH, "Ready for when a probe is wired.", "lodi");

        assertThat(updated.getSoilMonitoringMode()).isEqualTo(SoilMonitoringMode.MANUAL);
        assertThat(updated.getSoilMoistureBand()).isEqualTo(SoilMoistureBand.HIGH);
        assertThat(updated.getVersion()).isEqualTo(3);
    }

    // ...and the band must survive a later mode change in the other direction.
    @Test
    void theBandIsCarriedForwardWhenTheModeChanges() {
        profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.HIGH, "Thirsty.", "lodi");

        CropMonitoringProfile updated = profileService.changeSoilMonitoringMode(
                crop.getId(), SoilMonitoringMode.MANUAL, "No probe wired.", "lodi");

        assertThat(updated.getSoilMoistureBand()).isEqualTo(SoilMoistureBand.HIGH);
        assertThat(updated.getSoilDryThresholdIndex()).isEqualTo(50.0);
    }

    @Test
    void settingTheBandItAlreadyHasCreatesNoVersion() {
        CropMonitoringProfile unchanged = profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.LOW, "Same as before.", "lodi");

        assertThat(unchanged.getVersion()).isEqualTo(1);
        assertThat(profileRepository.findAllByCropIdOrderByVersionDesc(crop.getId())).hasSize(1);
    }

    @Test
    void aRationaleIsRequired() {
        assertThatThrownBy(() -> profileService.changeSoilMoistureBand(
                crop.getId(), SoilMoistureBand.HIGH, "   ", "lodi"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("rationale");
    }

    @Test
    void aBandIsRequired() {
        assertThatThrownBy(() -> profileService.changeSoilMoistureBand(
                crop.getId(), null, "No band given.", "lodi"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("band is required");
    }

    @Test
    void anUnknownCropIsRejected() {
        assertThatThrownBy(() -> profileService.changeSoilMoistureBand(
                999_999L, SoilMoistureBand.HIGH, "Nobody home.", "lodi"))
                .isInstanceOf(CropNotFoundException.class);
    }
}
