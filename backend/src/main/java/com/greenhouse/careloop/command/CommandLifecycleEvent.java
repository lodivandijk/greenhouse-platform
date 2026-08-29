package com.greenhouse.careloop.command;

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
@Table(name = "command_lifecycle_event")
public class CommandLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "command_id")
    private Long commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private CommandLifecycleEventType eventType;

    @Column(name = "reason_text")
    private String reasonText;

    @Column(name = "deferred_until")
    private Instant deferredUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private ActorType actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "request_id")
    private String requestId;

    public CommandLifecycleEvent() {
    }

    public CommandLifecycleEvent(Long commandId, CommandLifecycleEventType eventType, String reasonText,
                                 Instant deferredUntil, ActorType actorType, String actorId,
                                 Instant occurredAt, String requestId) {
        this.commandId = commandId;
        this.eventType = eventType;
        this.reasonText = reasonText;
        this.deferredUntil = deferredUntil;
        this.actorType = actorType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getCommandId() { return commandId; }
    public CommandLifecycleEventType getEventType() { return eventType; }
    public String getReasonText() { return reasonText; }
    public Instant getDeferredUntil() { return deferredUntil; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRequestId() { return requestId; }
}
