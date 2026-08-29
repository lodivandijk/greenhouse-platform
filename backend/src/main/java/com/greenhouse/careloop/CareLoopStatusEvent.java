package com.greenhouse.careloop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "care_loop_status_event")
public class CareLoopStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CareLoopStatus status;

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

    public CareLoopStatusEvent() {
    }

    public CareLoopStatusEvent(Long careLoopId, CareLoopStatus status, String reasonCode, String reasonText,
                               ActorType actorType, String actorId, Instant occurredAt, String requestId) {
        this.careLoopId = careLoopId;
        this.status = status;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.actorType = actorType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getCareLoopId() { return careLoopId; }
    public CareLoopStatus getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRequestId() { return requestId; }
}
