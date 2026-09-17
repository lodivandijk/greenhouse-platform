package com.greenhouse.briefing;

import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropObservationMetric;
import com.greenhouse.crop.CropObservationService;
import com.greenhouse.crop.CropObservationSource;
import com.greenhouse.crop.CropObservationValueType;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropService;
import com.greenhouse.crop.HarvestService;
import com.greenhouse.crop.HarvestUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The fact sheet is the model's only permitted source, so a false statement in
// it is a false statement in the briefing - and the model has no way to catch
// it.
//
// This line used to read actions alone and then announce "No work has ever been
// recorded for this crop": a claim about four record types made by looking at
// one. Basil had a harvest and two observations, and the very same snapshot
// carried them in latestHarvest and latestObservation.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class BriefingCropHistoryTest {

    @Autowired private CropService cropService;
    @Autowired private CropRepository cropRepository;
    @Autowired private HarvestService harvestService;
    @Autowired private CropObservationService cropObservationService;
    @Autowired private DailyBriefingService briefingService;

    private Crop crop;

    @BeforeEach
    void createCrop() {
        Long cropId = cropService.createCrop(
                "Basil", null, "planter-history-test", Instant.now(), "crop history test").id();
        crop = cropRepository.findById(cropId).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        harvestService.getHarvestHistory(crop.getId())
                .forEach(harvest -> harvestService.deleteHarvest(harvest.id()));
        cropObservationService.getObservationHistory(crop.getId())
                .forEach(observation -> cropObservationService.deleteObservation(observation.id()));
        cropRepository.findById(crop.getId()).ifPresent(cropRepository::delete);
    }

    private String factLine() {
        Map<String, Object> briefing = briefingService.buildCurrentBriefing();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> crops = (List<Map<String, Object>>) briefing.get("crops");
        return crops.stream()
                .filter(entry -> crop.getId().equals(entry.get("cropId")))
                .map(entry -> String.valueOf(entry.get("factLine")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("crop missing from the briefing"));
    }

    private void recordHarvest(Instant at) {
        harvestService.recordHarvest(crop.getId(), at, 41.0, HarvestUnit.GRAMS, "thinning harvest");
    }

    private void recordObservation(Instant at, String text) {
        cropObservationService.recordObservation(
                crop.getId(), CropObservationMetric.OTHER, CropObservationValueType.TEXT,
                null, text, null, null, CropObservationSource.HUMAN, 1.0, at, null, null);
    }

    // Basil's exact situation: a harvest and observations, no actions at all.
    @Test
    void aCropWithHarvestsAndObservationsIsNeverCalledUnrecorded() {
        Instant aWhileAgo = Instant.now().minus(Duration.ofDays(19));
        recordHarvest(aWhileAgo);
        recordObservation(aWhileAgo.plus(Duration.ofDays(2)), "Marks on the leaves");

        String line = factLine();

        assertThat(line)
                .as("a crop with a harvest and an observation has plainly been recorded")
                .doesNotContain("Nothing has ever been recorded");
        assertThat(line).contains("Last recorded:");
        assertThat(line).contains("harvest").contains("observation");
    }

    // "Nothing lately" must not be reported as "nothing ever" - that is the
    // distinction the old sentence collapsed.
    @Test
    void historyOutsideTheWindowIsReportedAsHowLongAgoNotAsAbsence() {
        recordHarvest(Instant.now().minus(Duration.ofDays(19)));

        String line = factLine();

        assertThat(line).contains("19 days ago");
        assertThat(line).doesNotContain("Recorded in window");
    }

    @Test
    void recordsInsideTheWindowAreListedAsSuch() {
        recordHarvest(Instant.now().minus(Duration.ofHours(2)));
        recordObservation(Instant.now().minus(Duration.ofHours(1)), "Leaf marks spreading");

        String line = factLine();

        assertThat(line).contains("Recorded in window:");
        assertThat(line).contains("1 harvest").contains("1 observation");
    }

    // The claim is still available when it is genuinely true.
    @Test
    void aCropWithNoRecordsAtAllStillSaysSo() {
        assertThat(factLine()).contains("Nothing has ever been recorded for this crop.");
    }
}
