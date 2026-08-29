package com.greenhouse.careloop.decision;

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

@Entity
@Table(name = "decision_lifecycle_event")
public class DecisionLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "decision_id")
    private Long decisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private DecisionLifecycleEventType eventType;

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

    public DecisionLifecycleEvent() {
    }

    public DecisionLifecycleEvent(Long decisionId, DecisionLifecycleEventType eventType, String reasonText,
                                  ActorType actorType, String actorId, Instant occurredAt, String requestId) {
        this.decisionId = decisionId;
        this.eventType = eventType;
        this.reasonText = reasonText;
        this.actorType = actorType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getDecisionId() { return decisionId; }
    public DecisionLifecycleEventType getEventType() { return eventType; }
    public String getReasonText() { return reasonText; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRequestId() { return requestId; }
}
