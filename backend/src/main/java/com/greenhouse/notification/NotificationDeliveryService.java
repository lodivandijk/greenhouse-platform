package com.greenhouse.notification;

import com.greenhouse.notification.delivery.DeliveryRequest;
import com.greenhouse.notification.delivery.DeliveryResult;
import com.greenhouse.notification.delivery.NotificationDeliveryPort;
import com.greenhouse.notification.rendering.NotificationRenderer;
import com.greenhouse.notification.rendering.RenderedNotification;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Turns intents into delivered messages.
//
// The ordering here is deliberate and load-bearing: ATTEMPTED is committed
// BEFORE the network call, and the result is committed after, with no
// transaction open across SMTP. A held transaction would pin a database
// connection for the length of a network timeout, and a crash mid-send would
// otherwise leave no trace that an attempt happened at all.
@Service
public class NotificationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationIntentRepository intentRepository;
    private final NotificationDeliveryEventRepository deliveryEventRepository;
    private final NotificationDeliveryEventWriter eventWriter;
    private final NotificationPolicyService policyService;
    private final NotificationRenderer renderer;
    private final NotificationProperties properties;
    private final List<NotificationDeliveryPort> deliveryPorts;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public NotificationDeliveryService(
            NotificationIntentRepository intentRepository,
            NotificationDeliveryEventRepository deliveryEventRepository,
            NotificationDeliveryEventWriter eventWriter,
            NotificationPolicyService policyService,
            NotificationRenderer renderer,
            NotificationProperties properties,
            List<NotificationDeliveryPort> deliveryPorts,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.eventWriter = eventWriter;
        this.policyService = policyService;
        this.renderer = renderer;
        this.properties = properties;
        this.deliveryPorts = deliveryPorts;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public int deliverPending() {
        if (deliveryPorts.isEmpty()) {
            // No channel configured - intents still accumulate and will be
            // delivered whenever one is switched on.
            return 0;
        }

        Instant now = clock.instant();
        int delivered = 0;

        for (NotificationIntent intent : intentRepository.findAllByNotBeforeLessThanEqualOrderByCreatedAtAsc(now)) {
            for (NotificationDeliveryPort port : deliveryPorts) {
                try {
                    if (deliverOne(intent, port, now)) {
                        delivered++;
                    }
                } catch (Exception e) {
                    // One bad intent must never stop the others, nor kill the
                    // scheduled sweep.
                    LOGGER.error(
                            "Notification delivery failed unexpectedly: intentId={} channel={}",
                            intent.getId(), port.channel(), e
                    );
                }
            }
        }

        return delivered;
    }

    private boolean deliverOne(NotificationIntent intent, NotificationDeliveryPort port, Instant now) {
        Optional<NotificationDeliveryEvent> latest = deliveryEventRepository
                .findFirstByNotificationIntentIdAndChannelOrderByOccurredAtDescIdDesc(intent.getId(), port.channel());

        if (latest.map(event -> event.getEventType().isTerminal()).orElse(false)) {
            return false;
        }

        // A failure is only retried once its backoff has elapsed.
        if (latest.isPresent()
                && latest.get().getEventType() == NotificationDeliveryEventType.FAILED
                && latest.get().getNextAttemptAt() != null
                && now.isBefore(latest.get().getNextAttemptAt())) {
            return false;
        }

        // Mid-flight from a previous crash: ATTEMPTED with no outcome recorded.
        // Retrying is correct - at-least-once is the honest guarantee, and the
        // deterministic Message-ID gives the receiving server a chance to
        // collapse the duplicate.
        int attemptNumber = latest.map(event -> event.getAttemptNumber() + 1).orElse(1);

        if (isExpired(intent, now)) {
            eventWriter.append(terminal(intent, port, attemptNumber,
                    NotificationDeliveryEventType.ABANDONED, now,
                    "EXPIRED", "No longer relevant; not delivered."));
            count("abandoned", port, "expired");
            return false;
        }

        // Re-checked immediately before sending, not when the intent was
        // created: the human may have dealt with it in the meantime.
        if (!policyService.isStillCurrent(intent)) {
            eventWriter.append(terminal(intent, port, attemptNumber,
                    NotificationDeliveryEventType.SUPPRESSED, now,
                    "STATE_MOVED_ON", "The care loop closed or progressed before this was sent."));
            count("suppressed", port, "state_moved_on");
            LOGGER.info("Notification suppressed as stale: intentId={} channel={}",
                    intent.getId(), port.channel());
            return false;
        }

        if (attemptNumber > properties.maxDeliveryAttempts()) {
            eventWriter.append(terminal(intent, port, attemptNumber,
                    NotificationDeliveryEventType.ABANDONED, now,
                    "MAX_ATTEMPTS", "Giving up after " + properties.maxDeliveryAttempts() + " attempts."));
            count("abandoned", port, "max_attempts");
            return false;
        }

        RenderedNotification rendered = renderer.render(intent);
        String recipient = recipientFor(port);
        DeliveryRequest request = new DeliveryRequest(
                intent.getId(), intent.getIntentType(), intent.getPriority(), recipient,
                rendered.subject(), rendered.plainTextBody(), rendered.htmlBody(),
                deterministicMessageId(intent, port)
        );

        // Committed before the network call, so an attempt is never invisible.
        eventWriter.append(event(intent, port, attemptNumber,
                NotificationDeliveryEventType.ATTEMPTED, now, recipient));

        long startedNanos = System.nanoTime();
        DeliveryResult result;
        try {
            // No transaction is open here. This is the point of the whole shape.
            result = port.deliver(request);
        } catch (Exception e) {
            result = DeliveryResult.retryable("ADAPTER_EXCEPTION", sanitise(e.getMessage()));
        }
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;

        Instant completedAt = clock.instant();
        return recordOutcome(intent, port, attemptNumber, recipient, result, completedAt, durationMs);
    }

    private boolean recordOutcome(
            NotificationIntent intent, NotificationDeliveryPort port, int attemptNumber,
            String recipient, DeliveryResult result, Instant at, long durationMs
    ) {
        switch (result.status()) {
            case SUCCESS -> {
                NotificationDeliveryEvent sent = event(intent, port, attemptNumber,
                        NotificationDeliveryEventType.SENT, at, recipient);
                sent.setProviderMessageId(result.providerMessageId());
                eventWriter.append(sent);
                count("sent", port, "ok");
                LOGGER.info(
                        "Notification sent: intentId={} type={} priority={} channel={} attempt={} durationMs={}",
                        intent.getId(), intent.getIntentType(), intent.getPriority(),
                        port.channel(), attemptNumber, durationMs
                );
                return true;
            }
            case PERMANENT_FAILURE -> {
                // Bad credentials or a rejected address will not fix themselves;
                // retrying forever would just fill the log.
                NotificationDeliveryEvent abandoned = event(intent, port, attemptNumber,
                        NotificationDeliveryEventType.ABANDONED, at, recipient);
                abandoned.setErrorCode(result.errorCode());
                abandoned.setErrorMessage(sanitise(result.errorMessage()));
                eventWriter.append(abandoned);
                count("abandoned", port, "permanent_failure");
                LOGGER.error(
                        "Notification abandoned after permanent failure: intentId={} channel={} code={}",
                        intent.getId(), port.channel(), result.errorCode()
                );
                return false;
            }
            default -> {
                boolean lastAttempt = attemptNumber >= properties.maxDeliveryAttempts();
                NotificationDeliveryEvent failed = event(intent, port, attemptNumber,
                        lastAttempt ? NotificationDeliveryEventType.ABANDONED
                                : NotificationDeliveryEventType.FAILED,
                        at, recipient);
                failed.setErrorCode(result.errorCode());
                failed.setErrorMessage(sanitise(result.errorMessage()));
                if (!lastAttempt) {
                    // Persisted, so the retry schedule survives a restart.
                    failed.setNextAttemptAt(at.plus(properties.retryDelayForAttempt(attemptNumber)));
                }
                eventWriter.append(failed);
                count(lastAttempt ? "abandoned" : "failed", port, "retryable_failure");
                LOGGER.warn(
                        "Notification delivery failed: intentId={} channel={} attempt={} code={} nextAttemptAt={}",
                        intent.getId(), port.channel(), attemptNumber,
                        result.errorCode(), failed.getNextAttemptAt()
                );
                return false;
            }
        }
    }

    // Error codes are a bounded, sanitised vocabulary, so they are safe as a
    // tag; a provider message never is.
    private void count(String outcome, NotificationDeliveryPort port, String reason) {
        meterRegistry.counter(
                "greenhouse.notifications.delivery",
                "outcome", outcome,
                "channel", port.channel(),
                "reason", reason
        ).increment();
    }

    private boolean isExpired(NotificationIntent intent, Instant now) {
        return intent.getExpiresAt() != null && now.isAfter(intent.getExpiresAt());
    }

    private NotificationDeliveryEvent event(
            NotificationIntent intent, NotificationDeliveryPort port, int attemptNumber,
            NotificationDeliveryEventType type, Instant at, String recipient
    ) {
        return new NotificationDeliveryEvent(
                intent.getId(), port.channel(), type, attemptNumber, recipient, at);
    }

    private NotificationDeliveryEvent terminal(
            NotificationIntent intent, NotificationDeliveryPort port, int attemptNumber,
            NotificationDeliveryEventType type, Instant at, String code, String message
    ) {
        NotificationDeliveryEvent terminal =
                event(intent, port, attemptNumber, type, at, recipientFor(port));
        terminal.setErrorCode(code);
        terminal.setErrorMessage(message);
        return terminal;
    }

    private String recipientFor(NotificationDeliveryPort port) {
        if ("EMAIL".equals(port.channel())) {
            return properties.channels().email().to();
        }
        return port.channel();
    }

    // Stable across every retry of the same intent on the same channel.
    private String deterministicMessageId(NotificationIntent intent, NotificationDeliveryPort port) {
        return "<greenhouse-notification-" + intent.getId() + "-"
                + port.channel().toLowerCase() + "@"
                + properties.channels().email().messageIdDomain() + ">";
    }

    // Delivery errors can echo back connection strings; keep them short and
    // never let a credential reach the database or the log.
    private String sanitise(String message) {
        if (message == null) {
            return null;
        }
        String collapsed = message.replaceAll("(?i)(password|secret|token|auth)\\s*[=:]\\s*\\S+", "$1=***");
        return collapsed.length() > 500 ? collapsed.substring(0, 500) + "..." : collapsed;
    }

    public List<NotificationDeliveryEvent> historyFor(Long intentId) {
        return deliveryEventRepository.findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(intentId);
    }

    public Duration retryDelayForAttempt(int attemptNumber) {
        return properties.retryDelayForAttempt(attemptNumber);
    }
}
