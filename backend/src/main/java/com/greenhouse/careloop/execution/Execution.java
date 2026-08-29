package com.greenhouse.careloop.execution;

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

// What the human actually did. The requested parameters stay on the Command;
// actualParameters records reality, so "asked for 300ml, gave 200ml" survives
// as two distinct facts. Immutable - a correction is a new row with
// correctsExecutionId set (ADR-021).
@Entity
@Table(name = "execution")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "command_id")
    private Long commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private ExecutionResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actual_parameters_json")
    private Map<String, Object> actualParameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "performed_by")
    private ActorType performedBy;

    @Column(name = "performed_by_actor_id")
    private String performedByActorId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "corrects_execution_id")
    private Long correctsExecutionId;

    public Execution() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCareLoopId() { return careLoopId; }
    public void setCareLoopId(Long careLoopId) { this.careLoopId = careLoopId; }
    public Long getCommandId() { return commandId; }
    public void setCommandId(Long commandId) { this.commandId = commandId; }
    public ExecutionResult getResult() { return result; }
    public void setResult(ExecutionResult result) { this.result = result; }
    public Map<String, Object> getActualParameters() { return actualParameters; }
    public void setActualParameters(Map<String, Object> actualParameters) { this.actualParameters = actualParameters; }
    public ActorType getPerformedBy() { return performedBy; }
    public void setPerformedBy(ActorType performedBy) { this.performedBy = performedBy; }
    public String getPerformedByActorId() { return performedByActorId; }
    public void setPerformedByActorId(String performedByActorId) { this.performedByActorId = performedByActorId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getCorrectsExecutionId() { return correctsExecutionId; }
    public void setCorrectsExecutionId(Long correctsExecutionId) { this.correctsExecutionId = correctsExecutionId; }
}
