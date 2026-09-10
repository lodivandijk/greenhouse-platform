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

    // A bare index on a lock screen is the easiest number in this system to
    // misread, and the caveat that makes it meaningful lives in the email.
    @Test
    void aPushQuotesNoMoistureIndex() {
        RenderedNotification push = renderer.render(careLoop(
                NotificationIntentType.ACTION_REQUIRED, NotificationPriority.WARNING), PUSH);

        assertThat(push.plainTextBody()).doesNotContain("index");
        assertThat(push.plainTextBody()).doesNotContain("dry line");
        assertThat(push.plainTextBody()).doesNotContain("wet ceiling");
        // The crop id is fine; a bare measurement is not.
        assertThat(push.plainTextBody()).doesNotContainPattern("\\b\\d+(\\.\\d+)?\\s*(points|%|C)\\b");
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

        assertThat(push.subject()).isEqualTo("Greenhouse - daily briefing");
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
