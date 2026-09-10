package com.greenhouse.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

// All notification configuration, including the recipient. The address is never
// embedded in Java, migrations or tests - it is bound here so a different
// deployment simply configures a different one.
@Validated
@ConfigurationProperties(prefix = "greenhouse.notifications")
public record NotificationProperties(
        boolean enabled,
        Duration sweepInterval,
        Duration initialDelay,
        Duration reminderInterval,
        boolean recoveryEmailEnabled,
        int maxDeliveryAttempts,
        // Used to build the deterministic Message-ID. That is an idempotency
        // key that happens to be carried in an email header, not an email
        // setting - a push channel needs one too (ADR-031).
        String messageIdDomain,
        Channels channels
) {

    // The escalating backoff from the spec. Attempt n uses entry n-1; beyond the
    // end of the list the intent is abandoned.
    private static final List<Duration> DEFAULT_RETRY_DELAYS = List.of(
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(12)
    );

    // A briefing older than this is history, not news - retrying it would
    // deliver yesterday's weather.
    private static final Duration BRIEFING_RELEVANCE_WINDOW = Duration.ofHours(18);

    public NotificationProperties {
        // Duration is not supported by @Positive, matching EvaluationProperties.
        if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
            throw new IllegalArgumentException("greenhouse.notifications.sweep-interval must be positive");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("greenhouse.notifications.initial-delay must not be negative");
        }
        if (reminderInterval == null || reminderInterval.isZero() || reminderInterval.isNegative()) {
            throw new IllegalArgumentException("greenhouse.notifications.reminder-interval must be positive");
        }
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException("greenhouse.notifications.max-delivery-attempts must be at least 1");
        }
        if (channels == null) {
            throw new IllegalArgumentException("greenhouse.notifications.channels is required");
        }
        if (messageIdDomain == null || messageIdDomain.isBlank()) {
            messageIdDomain = "greenhouse.local";
        }
    }

    public List<Duration> retryDelays() {
        return DEFAULT_RETRY_DELAYS;
    }

    public Duration briefingRelevanceWindow() {
        return BRIEFING_RELEVANCE_WINDOW;
    }

    public Duration retryDelayForAttempt(int attemptNumber) {
        int index = Math.max(0, attemptNumber - 1);
        List<Duration> delays = retryDelays();
        return index < delays.size() ? delays.get(index) : delays.get(delays.size() - 1);
    }

    public record Channels(Email email, Ntfy ntfy) {
        public Channels {
            if (email == null) {
                throw new IllegalArgumentException("greenhouse.notifications.channels.email is required");
            }
            if (ntfy == null) {
                ntfy = new Ntfy(false, null, null, null, null);
            }
        }
    }

    public record Ntfy(
            boolean enabled,
            String baseUrl,
            String topic,
            // Optional. Only needed for a protected topic or a self-hosted
            // server; ntfy.sh's public topics take none.
            String token,
            // Which kinds of message this phone wants. Empty means all of them.
            java.util.List<NotificationIntentType> intentTypes
    ) {
        public Ntfy {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://ntfy.sh";
            }
            // Trailing slashes turn into a double slash in the published URL,
            // which ntfy answers with a 404 rather than a useful error.
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            intentTypes = intentTypes == null ? java.util.List.of() : java.util.List.copyOf(intentTypes);

            if (enabled && (topic == null || topic.isBlank())) {
                throw new IllegalArgumentException(
                        "greenhouse.notifications.channels.ntfy.topic is required when ntfy is enabled - "
                                + "without it there is nowhere to publish.");
            }
        }

        public boolean carries(NotificationIntentType intentType) {
            return intentTypes.isEmpty() || intentTypes.contains(intentType);
        }

        public String publishUrl() {
            return baseUrl + "/" + topic;
        }
    }

    public record Email(
            boolean enabled,
            String from,
            String to,
            String messageIdDomain
    ) {
        public Email {
            // Only validated when email is actually switched on, so the
            // application starts fine with no mail configuration at all.
            if (enabled) {
                if (from == null || from.isBlank()) {
                    throw new IllegalArgumentException(
                            "greenhouse.notifications.channels.email.from is required when email is enabled");
                }
                if (to == null || to.isBlank()) {
                    throw new IllegalArgumentException(
                            "greenhouse.notifications.channels.email.to is required when email is enabled");
                }
            }
            if (messageIdDomain == null || messageIdDomain.isBlank()) {
                messageIdDomain = "greenhouse.local";
            }
        }
    }
}
