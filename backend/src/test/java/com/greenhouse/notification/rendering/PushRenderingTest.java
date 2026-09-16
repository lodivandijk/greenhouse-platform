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

// A push is two lines on a lock screen with no tables underneath and no caveat
// in sight. What it may and may not say is therefore stricter than email, not
// looser (ADR-031).
class PushRenderingTest {

    private static final NotificationRenderer.ChannelFormat PUSH =
            NotificationRenderer.ChannelFormat.PUSH;

    private final DeterministicNotificationRenderer renderer = new DeterministicNotificationRenderer();

    private NotificationIntent careLoop(NotificationIntentType type, NotificationPriority priority) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("careLoopId", 18L);
        payload.put("subjectType", "CROP");
        payload.put("subjectId", "12");
        payload.put("conditionType", "CROP_SOIL_MOISTURE_HIGH");
        payload.put("status", "AWAITING_HUMAN_REVIEW");
        payload.put("nextRequiredAction", "Review the evidence and decide what to do.");
        payload.put("assessments", List.of());

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(type);
        intent.setPriority(priority);
        intent.setCareLoopId(18L);
        intent.setPayload(payload);
        intent.setCreatedAt(Instant.now());
        return intent;
    }

    private NotificationIntent briefing(String headline, String text) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headline", headline);
        summary.put("text", text);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("greenhouseDay", "2026-09-10");
        payload.put("isUpdate", false);
        payload.put("briefing", Map.of("summary", summary));

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(NotificationIntentType.DAILY_BRIEFING);
        intent.setPriority(NotificationPriority.NORMAL);
        intent.setBriefingSnapshotId(1L);
        intent.setPayload(payload);
        intent.setCreatedAt(Instant.now());
        return intent;
    }

    @Test
    void aPushCarriesNoHtmlAndStaysShort() {
        RenderedNotification push = renderer.render(careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.WARNING), PUSH);

        assertThat(push.htmlBody()).isNull();
        assertThat(push.plainTextBody().length())
                .as("a lock screen shows a couple of lines")
                .isLessThan(240);
        assertThat(push.subject()).isEqualTo("Greenhouse - action required");
        assertThat(push.plainTextBody()).contains("crop soil moisture high").contains("crop 12");
        assertThat(push.plainTextBody()).contains("Review the evidence");
    }

    private NotificationIntent flagged(String code, Map<String, Object> evidence) {
        NotificationIntent intent = careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.WARNING);
        Map<String, Object> payload = intent.getPayload();
        payload.put("greenhouseId", "greenhouse-01");
        payload.put("subjectSpecies", "Oregano");

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("assessmentId", 7L);
        assessment.put("code", code);
        assessment.put("severity", "WARNING");
        assessment.put("message", "the engine's own sentence");
        assessment.put("cropId", 12L);
        assessment.put("species", "Oregano");
        assessment.put("evidence", evidence);
        payload.put("assessments", List.of(assessment));
        return intent;
    }

    @Test
    void aTemperatureWarningNamesTheCropTheActualAndTheLimit() {
        RenderedNotification push = renderer.render(flagged("CROP_TEMPERATURE_ABOVE_PREFERRED",
                Map.of("actualTemperatureCelsius", 25.0, "preferredMaximumCelsius", 24.0)), PUSH);

        assertThat(push.plainTextBody())
                .contains("greenhouse-01")
                .contains("Crop 12 Oregano")
                .contains("temperature 25C")
                .contains("above its preferred maximum of 24C");
    }

    @Test
    void aTemperatureBelowTheMinimumReadsTheOtherWay() {
        RenderedNotification push = renderer.render(flagged("CROP_TEMPERATURE_BELOW_PREFERRED",
                Map.of("actualTemperatureCelsius", 12.4, "preferredMinimumCelsius", 15.0)), PUSH);

        assertThat(push.plainTextBody())
                .contains("temperature 12.4C")
                .contains("below its preferred minimum of 15C");
    }

    // The index gets its scale attached wherever it appears. A bare "43", or
    // worse "43%", is the easiest number in this system to misread.
    @Test
    void aMoistureIndexAlwaysCarriesItsScale() {
        RenderedNotification push = renderer.render(flagged("CROP_SOIL_MOISTURE_LOW",
                Map.of("moistureIndex", 43.0, "dryThresholdIndex", 50.0)), PUSH);

        assertThat(push.plainTextBody()).contains("soil 43 of 100");
        assertThat(push.plainTextBody()).contains("at or below its dry line of 50");
        assertThat(push.plainTextBody()).doesNotContain("43%");
    }

    @Test
    void aWetWarningComparesAgainstTheCeiling() {
        RenderedNotification push = renderer.render(flagged("CROP_SOIL_MOISTURE_HIGH",
                Map.of("moistureIndex", 100.0, "wetThresholdIndex", 75.0)), PUSH);

        assertThat(push.plainTextBody()).contains("soil 100 of 100");
        assertThat(push.plainTextBody()).contains("at or above its wet ceiling of 75");
    }

    // Several crops in one shared greenhouse loop: naming only the first would
    // hide the rest.
    @Test
    void everyFlaggedCropGetsItsOwnLine() {
        NotificationIntent intent = flagged("CROP_TEMPERATURE_ABOVE_PREFERRED",
                Map.of("actualTemperatureCelsius", 25.0, "preferredMaximumCelsius", 24.0));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("code", "CROP_TEMPERATURE_ABOVE_PREFERRED");
        second.put("cropId", 8L);
        second.put("species", "Basil");
        second.put("evidence", Map.of(
                "actualTemperatureCelsius", 25.0, "preferredMaximumCelsius", 27.0));

        List<Object> both = new java.util.ArrayList<>(
                (List<Object>) intent.getPayload().get("assessments"));
        both.add(second);
        intent.getPayload().put("assessments", both);

        String body = renderer.render(intent, PUSH).plainTextBody();

        assertThat(body).contains("Crop 12 Oregano").contains("Crop 8 Basil");
        assertThat(body.lines().count()).isGreaterThanOrEqualTo(2);
    }

    // An older intent, captured before the payload carried species or
    // greenhouse id, must still render something useful.
    @Test
    void anIntentWithoutTheNewFieldsStillRenders() {
        NotificationIntent intent = careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.WARNING);

        String body = renderer.render(intent, PUSH).plainTextBody();

        assertThat(body).contains("crop soil moisture high");
        assertThat(body).contains("Review the evidence");
    }

    @Test
    void anUnrecognisedCodeIsNeverRenderedAsAnEmptyLine() {
        RenderedNotification push = renderer.render(
                flagged("SOME_FUTURE_CODE", Map.of()), PUSH);

        assertThat(push.plainTextBody()).contains("some future code");
    }

    @Test
    void eachKindOfMessageIsDistinguishableAtAGlance() {
        assertThat(renderer.render(careLoop(
                NotificationIntentType.REMINDER, NotificationPriority.WARNING), PUSH).subject())
                .isEqualTo("Greenhouse - still waiting");
        assertThat(renderer.render(careLoop(
                NotificationIntentType.RECOVERY, NotificationPriority.NORMAL), PUSH).subject())
                .isEqualTo("Greenhouse - resolved");
        assertThat(renderer.render(careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.CRITICAL), PUSH).subject())
                .isEqualTo("Greenhouse - CRITICAL");
    }

    @Test
    void theBriefingPushUsesTheHeadlineWrittenForIt() {
        RenderedNotification push = renderer.render(
                briefing("Thyme is two days from dry; nothing else needs you.",
                        "Long form paragraphs that would not fit on a phone."), PUSH);

        assertThat(push.subject()).isEqualTo("Greenhouse - morning briefing");
        assertThat(push.plainTextBody()).isEqualTo("Thyme is two days from dry; nothing else needs you.");
        assertThat(push.plainTextBody()).doesNotContain("Long form");
    }

    // A model that ignores the format costs a clumsy headline, not a lost push.
    @Test
    void aMissingHeadlineFallsBackToTheFirstSentence() {
        RenderedNotification push = renderer.render(
                briefing(null, "The thyme is the one worth a look. It has been drying all week."), PUSH);

        assertThat(push.plainTextBody()).isEqualTo("The thyme is the one worth a look.");
        assertThat(push.plainTextBody()).doesNotContain("drying all week");
    }

    // No summary at all must not become a cheerful silence.
    @Test
    void noSummaryAtAllSaysSoRatherThanSendingNothing() {
        RenderedNotification push = renderer.render(briefing(null, null), PUSH);

        assertThat(push.plainTextBody()).contains("No summary");
        assertThat(push.plainTextBody()).contains("readings");
    }

    @Test
    void theEmailFormatIsUnchangedAndStillCarriesItsDetail() {
        RenderedNotification email = renderer.render(careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.WARNING),
                NotificationRenderer.ChannelFormat.EMAIL);

        assertThat(email.htmlBody()).isNotNull();
        assertThat(email.plainTextBody()).contains("not volumetric water content");
        assertThat(email.plainTextBody().length()).isGreaterThan(240);
    }
}
