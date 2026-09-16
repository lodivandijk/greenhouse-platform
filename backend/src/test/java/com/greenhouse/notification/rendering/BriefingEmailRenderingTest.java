package com.greenhouse.notification.rendering;

import com.greenhouse.notification.NotificationIntent;
import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.NotificationPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The briefing email is the one message worth opening, so its failure mode is
// being too long to read. What it must keep however short it gets is the
// moisture caveat and any statement that something is unmeasured (ADR-033).
class BriefingEmailRenderingTest {

    private static final NotificationRenderer.ChannelFormat EMAIL =
            NotificationRenderer.ChannelFormat.EMAIL;
    private static final NotificationRenderer.ChannelFormat PUSH =
            NotificationRenderer.ChannelFormat.PUSH;

    private final DeterministicNotificationRenderer renderer = new DeterministicNotificationRenderer();

    private Map<String, Object> crop(
            long id, String species, Double index, Double dry, Double wet, String soilReason) {
        Map<String, Object> soil = new LinkedHashMap<>();
        if (index != null) {
            soil.put("status", "MEASURED");
            soil.put("moistureIndex", index);
            soil.put("rawAdc", 1841);
            soil.put("freshness", "CURRENT");
        } else {
            soil.put("status", "UNKNOWN");
            soil.put("reason", soilReason);
        }

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("soilDryThresholdIndex", dry);
        prefs.put("soilWetThresholdIndex", wet);
        prefs.put("preferredTemperatureMinCelsius", 15.0);
        prefs.put("preferredTemperatureMaxCelsius", 24.0);
        prefs.put("soilMoistureStrategy", "EVENLY_MOIST");

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("cropId", id);
        entry.put("species", species);
        entry.put("soil", soil);
        entry.put("preferences", prefs);
        entry.put("assessments", List.of());
        entry.put("trend", Map.of(
                "direction", "FALLING",
                "changePerDayIndexPoints", 1.7,
                "daysObserved", 7,
                "projectedDaysUntilDryThreshold", 9.0));
        return entry;
    }

    private NotificationIntent briefing(String edition, List<Object> crops, List<Object> gaps) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("text", "The thyme is the one worth a look.");
        summary.put("headline", "Thyme is the one worth a look.");
        summary.put("attribution", "Written by claude-opus-5 from the readings below.");

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("edition", edition);
        inner.put("summary", summary);
        inner.put("greenhouse", Map.of(
                "status", "NORMAL", "freshness", "CURRENT",
                "temperatureCelsius", 20.3, "humidityPercent", 65.1));
        inner.put("crops", crops);
        inner.put("openCareLoops", List.of());
        inner.put("dataQualityGaps", gaps);
        inner.put("recentOutcomes", List.of(Map.of("result", "SUCCESS", "summary", "soil rose")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("greenhouseDay", "2026-09-16");
        payload.put("edition", edition);
        payload.put("isUpdate", false);
        payload.put("briefing", inner);

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(NotificationIntentType.DAILY_BRIEFING);
        intent.setPriority(NotificationPriority.NORMAL);
        intent.setBriefingSnapshotId(1L);
        intent.setPayload(payload);
        intent.setCreatedAt(Instant.now());
        return intent;
    }

    private NotificationIntent standardBriefing(String edition) {
        return briefing(edition,
                List.of(crop(10, "Mint", 57.0, 50.0, null, null),
                        crop(13, "Tarragon", null, 30.0, 75.0, "MANUAL_MONITORING")),
                List.of());
    }

    // --- editions ---------------------------------------------------------

    @Test
    void theSubjectNamesWhichEditionThisIs() {
        assertThat(renderer.render(standardBriefing("MORNING"), EMAIL).subject())
                .isEqualTo("[Greenhouse] Morning briefing - 2026-09-16");
        assertThat(renderer.render(standardBriefing("EVENING"), EMAIL).subject())
                .isEqualTo("[Greenhouse] Evening briefing - 2026-09-16");
    }

    @Test
    void thePushTitleNamesTheEditionToo() {
        assertThat(renderer.render(standardBriefing("EVENING"), PUSH).subject())
                .isEqualTo("Greenhouse - evening briefing");
    }

    // A payload written before editions existed was a morning briefing.
    @Test
    void anOlderPayloadWithoutAnEditionReadsAsMorning() {
        NotificationIntent intent = standardBriefing("MORNING");
        intent.getPayload().remove("edition");

        assertThat(renderer.render(intent, EMAIL).subject()).contains("Morning briefing");
    }

    // --- conciseness ------------------------------------------------------

    @Test
    void theDetailTheSnapshotKeepsIsNoLongerInTheEmail() {
        String body = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();

        assertThat(body).doesNotContain("Preferred:");
        assertThat(body).doesNotContain("raw ");
        assertThat(body).doesNotContain("RECENT OUTCOMES");
        assertThat(body).doesNotContain("Data freshness:");
    }

    @Test
    void aCropIsOneLineWithItsReadingItsBoundsAndItsDirection() {
        String body = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();

        assertThat(body).contains("Mint (crop 10)");
        assertThat(body).contains("soil 57 of 100 (dry 50)");
        assertThat(body).contains("falling");
        // A projection is always flagged as conditional.
        assertThat(body).contains("if unchanged");
    }

    // Brevity must never turn "we did not measure" into silence.
    @Test
    void anUnmeasuredCropStillSaysSo() {
        String body = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();

        assertThat(body).contains("Tarragon (crop 13)");
        assertThat(body).contains("not measured - monitored by hand");
    }

    @Test
    void theMoistureCaveatSurvivesTheTrim() {
        String body = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();

        assertThat(body).contains("not volumetric water content");
    }

    // A "no gaps" line appeared every day and told the reader nothing.
    @Test
    void theNotMeasuredSectionAppearsOnlyWhenThereIsSomethingInIt() {
        String clean = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();
        assertThat(clean).doesNotContain("NOT MEASURED\n");

        String withGap = renderer.render(briefing("MORNING",
                List.of(crop(10, "Mint", 57.0, 50.0, null, null)),
                List.of(Map.of("kind", "NO_SENSOR_ASSIGNED", "cropId", 13, "species", "Tarragon"))),
                EMAIL).plainTextBody();
        assertThat(withGap).contains("NOT MEASURED").contains("NO_SENSOR_ASSIGNED");
    }

    @Test
    void theSummaryStillLeads() {
        String body = renderer.render(standardBriefing("MORNING"), EMAIL).plainTextBody();

        assertThat(body.indexOf("The thyme is the one worth a look."))
                .as("the summary comes before the readings it is accountable to")
                .isLessThan(body.indexOf("CROPS"));
    }

    @Test
    void aMissingSummaryIsStatedWithTheRightEdition() {
        NotificationIntent intent = standardBriefing("EVENING");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary =
                (Map<String, Object>) ((Map<String, Object>) intent.getPayload().get("briefing")).get("summary");
        summary.remove("text");
        summary.put("unavailableReason", "no summary writer is configured.");

        String body = renderer.render(intent, EMAIL).plainTextBody();

        assertThat(body).contains("No summary this evening");
        assertThat(body).contains("readings below are unaffected");
    }
}
