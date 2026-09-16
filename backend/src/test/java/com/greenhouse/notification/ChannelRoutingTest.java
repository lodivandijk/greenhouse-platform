package com.greenhouse.notification;

import com.greenhouse.notification.delivery.DeliveryResult;
import com.greenhouse.notification.rendering.NotificationRenderer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Adding a second channel must not disturb the first, and a channel must be
// able to want less than the inbox does (ADR-031).
class ChannelRoutingTest {

    private NotificationProperties.Ntfy ntfy(
            boolean enabled, String baseUrl, String topic, List<NotificationIntentType> types) {
        return new NotificationProperties.Ntfy(enabled, baseUrl, topic, null, types);
    }

    @Test
    void anEnabledNtfyChannelWithoutATopicRefusesToStart() {
        assertThatThrownBy(() -> ntfy(true, null, "  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic is required");
    }

    @Test
    void aDisabledChannelNeedsNoTopic() {
        assertThat(ntfy(false, null, null, List.of()).enabled()).isFalse();
    }

    // A trailing slash produces a double slash in the URL, which ntfy answers
    // with a 404 rather than anything that explains itself.
    @Test
    void trailingSlashesAreStrippedFromTheBaseUrl() {
        assertThat(ntfy(true, "https://ntfy.sh///", "greenhouse-notifications", List.of()).publishUrl())
                .isEqualTo("https://ntfy.sh/greenhouse-notifications");
    }

    @Test
    void aSelfHostedBaseUrlIsHonoured() {
        assertThat(ntfy(true, "http://pi.local:8080", "greenhouse", List.of()).publishUrl())
                .isEqualTo("http://pi.local:8080/greenhouse");
    }

    @Test
    void anEmptyIntentTypeListMeansEveryKindOfMessage() {
        NotificationProperties.Ntfy all = ntfy(true, null, "greenhouse", List.of());

        for (NotificationIntentType type : NotificationIntentType.values()) {
            assertThat(all.carries(type)).as("%s", type).isTrue();
        }
    }

    @Test
    void aNarrowedChannelCarriesOnlyWhatItWasGiven() {
        NotificationProperties.Ntfy quiet = ntfy(true, null, "greenhouse",
                List.of(NotificationIntentType.ACTION_REQUIRED));

        assertThat(quiet.carries(NotificationIntentType.ACTION_REQUIRED)).isTrue();
        assertThat(quiet.carries(NotificationIntentType.REMINDER)).isFalse();
        assertThat(quiet.carries(NotificationIntentType.DAILY_BRIEFING)).isFalse();
    }

    // The Message-ID is an idempotency key. It must not depend on email being
    // configured, or a push-only deployment could not build one.
    @Test
    void theMessageIdDomainIsChannelNeutralAndAlwaysHasAValue() {
        NotificationProperties properties = new NotificationProperties(
                true, Duration.ofMinutes(5), Duration.ofSeconds(45), Duration.ofHours(12),
                false, 6, "  ",
                new NotificationProperties.Channels(
                        new NotificationProperties.Email(false, null, null, null, java.util.List.of()),
                        ntfy(true, null, "greenhouse", List.of())));

        assertThat(properties.messageIdDomain()).isEqualTo("greenhouse.local");
    }

    // The inbox now carries briefings only; warnings go to the phone (ADR-033).
    @Test
    void anEmailChannelNarrowedToBriefingsDeclinesWarnings() {
        NotificationProperties.Email briefingsOnly = new NotificationProperties.Email(
                true, "from@example.invalid", "to@example.invalid", null,
                List.of(NotificationIntentType.DAILY_BRIEFING));

        assertThat(briefingsOnly.carries(NotificationIntentType.DAILY_BRIEFING)).isTrue();
        assertThat(briefingsOnly.carries(NotificationIntentType.ACTION_REQUIRED)).isFalse();
        assertThat(briefingsOnly.carries(NotificationIntentType.REMINDER)).isFalse();
        assertThat(briefingsOnly.carries(NotificationIntentType.RECOVERY)).isFalse();
    }

    @Test
    void anEmailChannelWithNoFilterStillCarriesEverything() {
        NotificationProperties.Email all = new NotificationProperties.Email(
                true, "from@example.invalid", "to@example.invalid", null, List.of());

        for (NotificationIntentType type : NotificationIntentType.values()) {
            assertThat(all.carries(type)).as("%s", type).isTrue();
        }
    }

    @Test
    void aResultFromOneChannelSaysNothingAboutAnother() {
        // Retryable and permanent are per-attempt, per-channel classifications;
        // a rejected topic must not abandon the emailed copy.
        assertThat(DeliveryResult.permanent("NTFY_404", "no such topic").isSuccess()).isFalse();
        assertThat(DeliveryResult.retryable("NTFY_IO", "timeout").status())
                .isEqualTo(DeliveryResult.Status.RETRYABLE_FAILURE);
    }

    @Test
    void theTwoFormatsAreDistinct() {
        assertThat(NotificationRenderer.ChannelFormat.values())
                .containsExactlyInAnyOrder(
                        NotificationRenderer.ChannelFormat.EMAIL,
                        NotificationRenderer.ChannelFormat.PUSH);
    }
}
