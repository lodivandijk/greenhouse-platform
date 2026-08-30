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

// The renderer is where the platform's honesty either survives or quietly dies.
// An email is read once, in a hurry, on a phone - so a proposal must not read
// like a completed action, and a moisture index must not read like a moisture
// percentage.
class DeterministicNotificationRendererTest {

    private final DeterministicNotificationRenderer renderer = new DeterministicNotificationRenderer();

    private NotificationIntent careLoopIntent(Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("careLoopId", 42L);
        payload.put("subjectType", "CROP");
        payload.put("subjectId", "8");
        payload.put("conditionType", "CROP_SOIL_MOISTURE_LOW");
        payload.put("status", "AWAITING_HUMAN_REVIEW");
        payload.put("nextRequiredAction", "Review the evidence and decide what to do.");
        payload.put("openedAt", "2026-08-30T09:00:00Z");
        payload.put("assessments", List.of());
        payload.putAll(extraPayload);

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(NotificationIntentType.ACTION_REQUIRED);
        intent.setPriority(NotificationPriority.NORMAL);
        intent.setCareLoopId(42L);
        intent.setPayload(payload);
        intent.setCreatedAt(Instant.parse("2026-08-30T09:05:00Z"));
        return intent;
    }

    @Test
    void anUnapprovedDecisionIsNeverPresentedAsWorkToDo() {
        NotificationIntent intent = careLoopIntent(Map.of(
                "status", "AWAITING_DECISION_APPROVAL",
                "pendingDecisionId", 7L));

        String text = renderer.render(intent).plainTextBody();

        assertThat(text).contains("waiting for your approval");
        assertThat(text).contains("NOT");
        // The dangerous misreading: a human seeing "watered" and assuming the
        // greenhouse did it.
        assertThat(text).doesNotContain("has been carried out");
        assertThat(text).doesNotContain("was completed");
    }

    @Test
    void anApprovedCommandIsPresentedAsWorkTheHumanStillHasToDo() {
        NotificationIntent intent = careLoopIntent(Map.of(
                "status", "AWAITING_EXECUTION",
                "pendingCommandId", 11L));

        String text = renderer.render(intent).plainTextBody();

        assertThat(text).contains("waiting for you to carry it out");
    }

    @Test
    void everyCareLoopMessageCarriesTheMoistureCaveat() {
        String text = renderer.render(careLoopIntent(Map.of())).plainTextBody();

        assertThat(text).contains("not volumetric water content");
    }

    @Test
    void theHtmlBodyLoadsNothingFromTheNetworkAndRunsNoScript() {
        RenderedNotification rendered = renderer.render(careLoopIntent(Map.of()));

        String html = rendered.htmlBody().toLowerCase();
        // A remote image is a tracking pixel by another name, and a mail client
        // blocking it would leave the message half-rendered.
        assertThat(html).doesNotContain("<img");
        assertThat(html).doesNotContain("<script");
        assertThat(html).doesNotContain("http://");
        assertThat(html).doesNotContain("https://");
    }

    @Test
    void theSameIntentAlwaysRendersIdentically() {
        NotificationIntent intent = careLoopIntent(Map.of());

        RenderedNotification first = renderer.render(intent);
        RenderedNotification second = renderer.render(intent);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aBriefingSubjectDistinguishesAnUpdateFromTheFirstOfTheDay() {
        NotificationIntent original = briefingIntent(false);
        NotificationIntent update = briefingIntent(true);

        assertThat(renderer.render(original).subject()).contains("Daily briefing");
        assertThat(renderer.render(update).subject()).contains("Updated daily briefing");
    }

    private NotificationIntent briefingIntent(boolean isUpdate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("greenhouseDay", "2026-08-30");
        payload.put("generatedAt", "2026-08-30T06:00:00Z");
        payload.put("isUpdate", isUpdate);
        payload.put("briefing", Map.of(
                "greenhouse", Map.of("status", "OK", "freshness", "FRESH"),
                "crops", List.of(),
                "actionRequired", List.of(),
                "dataQuality", Map.of("gaps", List.of())));

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(NotificationIntentType.DAILY_BRIEFING);
        intent.setPriority(NotificationPriority.NORMAL);
        intent.setBriefingSnapshotId(1L);
        intent.setPayload(payload);
        intent.setCreatedAt(Instant.parse("2026-08-30T06:00:00Z"));
        return intent;
    }
}
