package com.greenhouse.careloop.outcome;

import com.greenhouse.careloop.ActorType;
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

// A human reviewing, qualifying or disputing an outcome. If the review carried
// a corrected result, resultingOutcomeId points at the new superseding Outcome
// - the original is never overwritten (ADR-021).
@Entity
@Table(name = "outcome_review_event")
public class OutcomeReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "outcome_id")
    private Long outcomeId;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "disputed")
    private Boolean disputed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_evidence_json")
    private Map<String, Object> additionalEvidence;

    @Column(name = "resulting_outcome_id")
    private Long resultingOutcomeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private ActorType actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "request_id")
    private String requestId;

    public OutcomeReviewEvent() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOutcomeId() { return outcomeId; }
    public void setOutcomeId(Long outcomeId) { this.outcomeId = outcomeId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Boolean getDisputed() { return disputed; }
    public void setDisputed(Boolean disputed) { this.disputed = disputed; }
    public Map<String, Object> getAdditionalEvidence() { return additionalEvidence; }
    public void setAdditionalEvidence(Map<String, Object> additionalEvidence) { this.additionalEvidence = additionalEvidence; }
    public Long getResultingOutcomeId() { return resultingOutcomeId; }
    public void setResultingOutcomeId(Long resultingOutcomeId) { this.resultingOutcomeId = resultingOutcomeId; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
