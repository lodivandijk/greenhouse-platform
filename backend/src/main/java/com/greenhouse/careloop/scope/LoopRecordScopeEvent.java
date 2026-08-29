package com.greenhouse.careloop.scope;

import com.greenhouse.careloop.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Append-only scope decision for one loop-record pair. Effective scope is the
// latest event for that pair. Scope is NOT lifecycle: a rejected decision or a
// resolved assessment normally stays IN_SCOPE as historical evidence; only an
// explicit reasoned override removes something (ADR-021).
@Entity
@Table(name = "loop_record_scope_event")
public class LoopRecordScopeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type")
    private LoopRecordType recordType;

    @Column(name = "record_id")
    private Long recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private LoopScope scope;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_text")
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private ActorType actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "request_id")
    private String requestId;

    public LoopRecordScopeEvent() {
    }

    public LoopRecordScopeEvent(Long careLoopId, LoopRecordType recordType, Long recordId, LoopScope scope,
                                String reasonCode, String reasonText, ActorType actorType, String actorId,
                                Instant occurredAt, String requestId) {
        this.careLoopId = careLoopId;
        this.recordType = recordType;
        this.recordId = recordId;
        this.scope = scope;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.actorType = actorType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getCareLoopId() { return careLoopId; }
    public LoopRecordType getRecordType() { return recordType; }
    public Long getRecordId() { return recordId; }
    public LoopScope getScope() { return scope; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRequestId() { return requestId; }
}
