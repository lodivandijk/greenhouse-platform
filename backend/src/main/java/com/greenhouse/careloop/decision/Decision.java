package com.greenhouse.careloop.decision;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.command.catalogue.CommandType;
import com.greenhouse.careloop.outcome.OutcomeEvaluationMethod;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// An immutable proposed response. Amending a decision creates a NEW row with
// supersedesDecisionId set; this row is never edited (ADR-021).
@Entity
@Table(name = "decision")
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private CommandType actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json")
    private Map<String, Object> parameters;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "expected_effect")
    private String expectedEffect;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_method")
    private OutcomeEvaluationMethod evaluationMethod;

    @Column(name = "evaluation_delay_seconds")
    private Long evaluationDelaySeconds;

    @Column(name = "evaluation_window_seconds")
    private Long evaluationWindowSeconds;

    @Column(name = "success_criteria")
    private String successCriteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_by")
    private ActorType proposedBy;

    @Column(name = "proposed_by_actor_id")
    private String proposedByActorId;

    @Column(name = "proposed_at")
    private Instant proposedAt;

    @Column(name = "supersedes_decision_id")
    private Long supersedesDecisionId;

    public Decision() {
    }

    public Duration evaluationDelay() {
        return evaluationDelaySeconds == null ? Duration.ZERO : Duration.ofSeconds(evaluationDelaySeconds);
    }

    public Duration evaluationWindow() {
        return evaluationWindowSeconds == null ? Duration.ZERO : Duration.ofSeconds(evaluationWindowSeconds);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCareLoopId() { return careLoopId; }
    public void setCareLoopId(Long careLoopId) { this.careLoopId = careLoopId; }
    public CommandType getActionType() { return actionType; }
    public void setActionType(CommandType actionType) { this.actionType = actionType; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getExpectedEffect() { return expectedEffect; }
    public void setExpectedEffect(String expectedEffect) { this.expectedEffect = expectedEffect; }
    public OutcomeEvaluationMethod getEvaluationMethod() { return evaluationMethod; }
    public void setEvaluationMethod(OutcomeEvaluationMethod evaluationMethod) { this.evaluationMethod = evaluationMethod; }
    public Long getEvaluationDelaySeconds() { return evaluationDelaySeconds; }
    public void setEvaluationDelaySeconds(Long evaluationDelaySeconds) { this.evaluationDelaySeconds = evaluationDelaySeconds; }
    public Long getEvaluationWindowSeconds() { return evaluationWindowSeconds; }
    public void setEvaluationWindowSeconds(Long evaluationWindowSeconds) { this.evaluationWindowSeconds = evaluationWindowSeconds; }
    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }
    public ActorType getProposedBy() { return proposedBy; }
    public void setProposedBy(ActorType proposedBy) { this.proposedBy = proposedBy; }
    public String getProposedByActorId() { return proposedByActorId; }
    public void setProposedByActorId(String proposedByActorId) { this.proposedByActorId = proposedByActorId; }
    public Instant getProposedAt() { return proposedAt; }
    public void setProposedAt(Instant proposedAt) { this.proposedAt = proposedAt; }
    public Long getSupersedesDecisionId() { return supersedesDecisionId; }
    public void setSupersedesDecisionId(Long supersedesDecisionId) { this.supersedesDecisionId = supersedesDecisionId; }
}
