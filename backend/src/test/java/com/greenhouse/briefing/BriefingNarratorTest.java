package com.greenhouse.briefing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The deterministic prose is both the fallback and the thing the model is
// checked against, so its honesty properties are tested directly: it must never
// turn "not measured" into "fine", never assert a cause, and never present a
// projection as a fact (ADR-029).
class BriefingNarratorTest {

    private final BriefingNarrator narrator = new BriefingNarrator();

    private CropNarrativeInput crop(CropSoilTrend trend, Double index, boolean manual) {
        return new CropNarrativeInput(
                10L, "Mint", manual, index, manual ? null : (index == null ? "no reading" : null),
                50.0, null, trend, List.of(), List.of(), null, 7, null);
    }

    // The mint case: a slow decline no single day would reveal.
    @Test
    void aDryingCropIsDescribedWithItsRateAndProjection() {
        CropSoilTrend falling = new CropSoilTrend(
                CropSoilTrend.Direction.FALLING, -5.7, 7, 78.0, 43.6, 2.3, false);

        String summary = narrator.cropSummary(crop(falling, 43.6, false));

        assertThat(summary).contains("drying for 7 days");
        assertThat(summary).contains("5.7 index points a day");
        assertThat(summary).contains("78").contains("44");
        assertThat(summary).contains("reaches its dry line in roughly 2 days");
    }

    // Observed, not diagnosed: the platform saw a number jump, not a person.
    @Test
    void aSharpRiseIsCalledConsistentWithWateringNotCausedByIt() {
        CropSoilTrend rising = new CropSoilTrend(
                CropSoilTrend.Direction.RISING, 8.0, 5, 40.0, 76.0, null, true);

        String summary = narrator.cropSummary(crop(rising, 76.0, false));

        assertThat(summary).contains("consistent with watering");
        assertThat(summary).doesNotContain("you watered");
    }

    @Test
    void aManuallyMonitoredCropIsSaidToBeUnwatchedNotFine() {
        String summary = narrator.cropSummary(crop(CropSoilTrend.unknown(), null, true));

        assertThat(summary).contains("not measured");
        assertThat(summary).contains("not being watched");
        assertThat(summary).doesNotContain("moisture index");
    }

    @Test
    void aCropWithNoReadingSaysSoRatherThanBeingOmitted() {
        String summary = narrator.cropSummary(crop(CropSoilTrend.unknown(), null, false));

        assertThat(summary).contains("no usable soil reading");
    }

    @Test
    void tooLittleHistoryIsStatedRatherThanImpliedAsSteady() {
        String summary = narrator.cropSummary(crop(CropSoilTrend.unknown(), 60.0, false));

        assertThat(summary).contains("not yet enough history");
        assertThat(summary).doesNotContain("steady");
    }

    @Test
    void aSingleDayWindowIsWordedInTheSingular() {
        CropNarrativeInput input = new CropNarrativeInput(
                10L, "Mint", false, 60.0, null, 50.0, null, CropSoilTrend.unknown(),
                List.of(), List.of(), 3, 1, null);

        String summary = narrator.cropSummary(input);

        assertThat(summary).contains("in the last day");
        assertThat(summary).doesNotContain("1 days");
    }

    @Test
    void aQuietGreenhouseSaysSoPlainly() {
        String summary = narrator.greenhouseSummary(
                0, 0, 0, 21.5, 60.0, "CURRENT", List.of());

        assertThat(summary).contains("Nothing is asking for your attention");
        assertThat(summary).contains("21.5°C");
    }

    @Test
    void flaggedCropsAndOpenLoopsAreBothReported() {
        String summary = narrator.greenhouseSummary(
                2, 1, 3, 24.0, 55.0, "CURRENT", List.of("Thyme", "Oregano"));

        assertThat(summary).contains("2 crops are flagged");
        assertThat(summary).contains("Thyme, Oregano");
        assertThat(summary).contains("One care loop is still open");
        assertThat(summary).contains("3 gaps");
    }

    // A gap must never be silently absent - that is the failure mode that let
    // an unmeasured crop look healthy.
    @Test
    void dataGapsAreCalledOutAsUnwatched() {
        String summary = narrator.greenhouseSummary(
                0, 0, 2, 20.0, 50.0, "CURRENT", List.of());

        assertThat(summary).contains("anything not measured is not being watched");
    }

    @Test
    void aStaleReadingIsLabelledRatherThanPresentedAsCurrent() {
        String summary = narrator.greenhouseSummary(
                0, 0, 0, 20.0, 50.0, "STALE", List.of());

        assertThat(summary).contains("stale");
    }
}
