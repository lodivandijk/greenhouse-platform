package com.greenhouse.notification.delivery;

import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.NotificationPriority;

// What an adapter needs to send a message, and nothing more. Deliberately a
// plain record: adapters never see JPA entities, so a new channel cannot grow a
// dependency on the persistence model.
public record DeliveryRequest(
        Long notificationIntentId,
        NotificationIntentType intentType,
        NotificationPriority priority,
        String recipient,
        String subject,
        String plainTextBody,
        String htmlBody,
        // Stable across retries of the same intent, which is what lets a
        // receiving server collapse an accidental duplicate.
        String deterministicMessageId
) {
}
