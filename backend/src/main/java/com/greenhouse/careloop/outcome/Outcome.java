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

// An evidence-based evaluation of an execution. Immutable - a re-evaluation is
// a new row with supersedesOutcomeId set (ADR-021).
@Entity
@Table(name = "outcome")
public class Outcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "decision_id")
    private Long decisionId;

    @Column(name = "command_id")
    private Long commandId;

    @Column(name = "execution_id")
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private OutcomeResult result;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "evaluation_window_start")
    private Instant evaluationWindowStart;

    @Column(name = "evaluation_window_end")
    private Instant evaluationWindowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json")
    private Map<String, Object> evidence;

    @Column(name = "summary")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluated_by")
    private ActorType evaluatedBy;

    @Column(name = "evaluated_by_actor_id")
    private String evaluatedByActorId;

    @Column(name = "supersedes_outcome_id")
    private Long supersedesOutcomeId;

    public Outcome() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCareLoopId() { return careLoopId; }
    public void setCareLoopId(Long careLoopId) { this.careLoopId = careLoopId; }
    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    public Long getCommandId() { return commandId; }
    public void setCommandId(Long commandId) { this.commandId = commandId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public OutcomeResult getResult() { return result; }
    public void setResult(OutcomeResult result) { this.result = result; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
    public Instant getEvaluationWindowStart() { return evaluationWindowStart; }
    public void setEvaluationWindowStart(Instant evaluationWindowStart) { this.evaluationWindowStart = evaluationWindowStart; }
    public Instant getEvaluationWindowEnd() { return evaluationWindowEnd; }
    public void setEvaluationWindowEnd(Instant evaluationWindowEnd) { this.evaluationWindowEnd = evaluationWindowEnd; }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public ActorType getEvaluatedBy() { return evaluatedBy; }
    public void setEvaluatedBy(ActorType evaluatedBy) { this.evaluatedBy = evaluatedBy; }
    public String getEvaluatedByActorId() { return evaluatedByActorId; }
    public void setEvaluatedByActorId(String evaluatedByActorId) { this.evaluatedByActorId = evaluatedByActorId; }
    public Long getSupersedesOutcomeId() { return supersedesOutcomeId; }
    public void setSupersedesOutcomeId(Long supersedesOutcomeId) { this.supersedesOutcomeId = supersedesOutcomeId; }
}
