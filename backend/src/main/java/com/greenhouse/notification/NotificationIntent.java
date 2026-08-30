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

// An immutable statement that the platform decided a message should exist.
//
// payload carries everything the renderer needs, captured at the moment the
// decision was made - so a message reads as it was meant to even if the
// greenhouse has since moved on (ADR-023).
@Entity
@Table(name = "notification_intent")
public class NotificationIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent_type")
    private NotificationIntentType intentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience")
    private NotificationAudience audience;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "briefing_snapshot_id")
    private Long briefingSnapshotId;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "deduplication_key")
    private String deduplicationKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private Map<String, Object> payload;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public NotificationIntent() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NotificationIntentType getIntentType() { return intentType; }
    public void setIntentType(NotificationIntentType intentType) { this.intentType = intentType; }
    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    public NotificationAudience getAudience() { return audience; }
    public void setAudience(NotificationAudience audience) { this.audience = audience; }
    public Long getCareLoopId() { return careLoopId; }
    public void setCareLoopId(Long careLoopId) { this.careLoopId = careLoopId; }
    public Long getBriefingSnapshotId() { return briefingSnapshotId; }
    public void setBriefingSnapshotId(Long briefingSnapshotId) { this.briefingSnapshotId = briefingSnapshotId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public void setDeduplicationKey(String deduplicationKey) { this.deduplicationKey = deduplicationKey; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
