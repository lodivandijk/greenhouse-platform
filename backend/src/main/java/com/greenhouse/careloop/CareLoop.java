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

// The correlation root for one actionable condition and everything that
// responds to it. Status is deliberately NOT a column here - it is projected
// from the loop's immutable event history (ADR-021).
@Entity
@Table(name = "care_loop")
public class CareLoop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_subject_type")
    private CareLoopSubjectType primarySubjectType;

    @Column(name = "primary_subject_id")
    private String primarySubjectId;

    @Column(name = "condition_type")
    private String conditionType;

    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by")
    private ActorType createdBy;

    public CareLoop() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CareLoopSubjectType getPrimarySubjectType() { return primarySubjectType; }
    public void setPrimarySubjectType(CareLoopSubjectType primarySubjectType) { this.primarySubjectType = primarySubjectType; }
    public String getPrimarySubjectId() { return primarySubjectId; }
    public void setPrimarySubjectId(String primarySubjectId) { this.primarySubjectId = primarySubjectId; }
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public ActorType getCreatedBy() { return createdBy; }
    public void setCreatedBy(ActorType createdBy) { this.createdBy = createdBy; }
}
