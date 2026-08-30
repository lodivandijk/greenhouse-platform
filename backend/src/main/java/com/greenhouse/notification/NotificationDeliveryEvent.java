package com.greenhouse.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

// One immutable row per delivery transition. A retry appends a new attempt; it
// never edits the failure that came before it.
@Entity
@Table(name = "notification_delivery_event")
public class NotificationDeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "notification_intent_id")
    private Long notificationIntentId;

    @Column(name = "channel")
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private NotificationDeliveryEventType eventType;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json")
    private Map<String, Object> metadata;

    public NotificationDeliveryEvent() {
    }

    public NotificationDeliveryEvent(
            Long notificationIntentId, String channel, NotificationDeliveryEventType eventType,
            Integer attemptNumber, String recipient, Instant occurredAt
    ) {
        this.notificationIntentId = notificationIntentId;
        this.channel = channel;
        this.eventType = eventType;
        this.attemptNumber = attemptNumber;
        this.recipient = recipient;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getNotificationIntentId() { return notificationIntentId; }
    public String getChannel() { return channel; }
    public NotificationDeliveryEventType getEventType() { return eventType; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public String getRecipient() { return recipient; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
