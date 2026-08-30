package com.greenhouse.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Read-only view of what was notified and what happened to it.
//
// This is the answer to "did I get told about this?" and "why did I get two
// emails?". It reports; it never resends, cancels or repairs. Delivery is
// driven entirely by the sweep, so exposing a write path here would create a
// second, racing dispatcher.
@Service
public class NotificationHistoryService {

    private static final int MAX_RESULTS = 50;

    private final NotificationIntentRepository intentRepository;
    private final NotificationDeliveryEventRepository deliveryEventRepository;

    public NotificationHistoryService(
            NotificationIntentRepository intentRepository,
            NotificationDeliveryEventRepository deliveryEventRepository
    ) {
        this.intentRepository = intentRepository;
        this.deliveryEventRepository = deliveryEventRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(
            Long careLoopId,
            NotificationIntentType intentType,
            String channel,
            Instant since
    ) {
        List<NotificationIntent> intents = selectIntents(careLoopId, since).stream()
                .filter(intent -> intentType == null || intent.getIntentType() == intentType)
                .sorted(Comparator.comparing(NotificationIntent::getCreatedAt).reversed())
                .limit(MAX_RESULTS)
                .toList();

        List<Map<String, Object>> entries = intents.stream()
                .map(intent -> describe(intent, channel))
                .toList();

        return Map.of(
                "notifications", entries,
                "returned", entries.size(),
                "maxResults", MAX_RESULTS,
                "note", "Read-only. SUPPRESSED means the situation resolved before the message was sent, "
                        + "which is normal. Delivery is at-least-once: a duplicate is possible if the "
                        + "process died between the provider accepting a message and SENT being recorded."
        );
    }

    private List<NotificationIntent> selectIntents(Long careLoopId, Instant since) {
        if (careLoopId != null) {
            return intentRepository.findAllByCareLoopIdOrderByCreatedAtDesc(careLoopId);
        }
        if (since != null) {
            return intentRepository.findAllByCreatedAtAfterOrderByCreatedAtDesc(since);
        }
        return intentRepository.findAllByOrderByCreatedAtDesc();
    }

    private Map<String, Object> describe(NotificationIntent intent, String channelFilter) {
        List<NotificationDeliveryEvent> events = deliveryEventRepository
                .findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(intent.getId()).stream()
                .filter(event -> channelFilter == null || channelFilter.equalsIgnoreCase(event.getChannel()))
                .toList();

        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("intentId", intent.getId());
        entry.put("intentType", intent.getIntentType().name());
        entry.put("priority", intent.getPriority().name());
        entry.put("careLoopId", intent.getCareLoopId());
        entry.put("briefingSnapshotId", intent.getBriefingSnapshotId());
        entry.put("createdAt", intent.getCreatedAt().toString());
        entry.put("notBefore", intent.getNotBefore().toString());
        entry.put("expiresAt", intent.getExpiresAt() == null ? null : intent.getExpiresAt().toString());
        entry.put("deliveryStatus", statusOf(events));
        entry.put("attempts", events.stream()
                .filter(event -> event.getEventType() == NotificationDeliveryEventType.ATTEMPTED)
                .count());
        entry.put("deliveryEvents", events.stream().map(this::describeEvent).toList());
        return entry;
    }

    private String statusOf(List<NotificationDeliveryEvent> events) {
        if (events.isEmpty()) {
            // No channel is enabled, or the sweep has not reached it yet.
            return "PENDING";
        }
        return events.get(events.size() - 1).getEventType().name();
    }

    private Map<String, Object> describeEvent(NotificationDeliveryEvent event) {
        Map<String, Object> described = new java.util.LinkedHashMap<>();
        described.put("eventType", event.getEventType().name());
        described.put("channel", event.getChannel());
        described.put("attemptNumber", event.getAttemptNumber());
        described.put("occurredAt", event.getOccurredAt().toString());
        described.put("errorCode", event.getErrorCode());
        described.put("errorMessage", event.getErrorMessage());
        described.put("nextAttemptAt",
                event.getNextAttemptAt() == null ? null : event.getNextAttemptAt().toString());
        return described;
    }
}
