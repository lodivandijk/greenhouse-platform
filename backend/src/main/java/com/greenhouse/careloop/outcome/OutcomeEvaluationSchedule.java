package com.greenhouse.careloop.outcome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// An execution waiting for its evaluation window to elapse. Persisted rather
// than held in memory so that pending evaluations survive a restart (ADR-021).
@Entity
@Table(name = "outcome_evaluation_schedule")
public class OutcomeEvaluationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "care_loop_id")
    private Long careLoopId;

    @Column(name = "evaluate_after")
    private Instant evaluateAfter;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public OutcomeEvaluationSchedule() {
    }

    public OutcomeEvaluationSchedule(Long executionId, Long careLoopId, Instant evaluateAfter,
                                     Instant windowEnd, Instant createdAt) {
        this.executionId = executionId;
        this.careLoopId = careLoopId;
        this.evaluateAfter = evaluateAfter;
        this.windowEnd = windowEnd;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public Long getCareLoopId() { return careLoopId; }
    public Instant getEvaluateAfter() { return evaluateAfter; }
    public Instant getWindowEnd() { return windowEnd; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
