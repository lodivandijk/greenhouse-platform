package com.greenhouse.briefing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

// One immutable structured briefing per greenhouse day. Regenerating creates a
// new version linked to the previous one rather than overwriting, so what was
// reported on a given morning stays exactly as it was reported (ADR-021).
@Entity
@Table(name = "daily_briefing_snapshot")
public class DailyBriefingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "greenhouse_day")
    private LocalDate greenhouseDay;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json")
    private Map<String, Object> snapshot;

    @Column(name = "missed_run_recovery")
    private Boolean missedRunRecovery;

    @Column(name = "supersedes_snapshot_id")
    private Long supersedesSnapshotId;

    public DailyBriefingSnapshot() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getGreenhouseDay() { return greenhouseDay; }
    public void setGreenhouseDay(LocalDate greenhouseDay) { this.greenhouseDay = greenhouseDay; }
    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public Map<String, Object> getSnapshot() { return snapshot; }
    public void setSnapshot(Map<String, Object> snapshot) { this.snapshot = snapshot; }
    public Boolean getMissedRunRecovery() { return missedRunRecovery; }
    public void setMissedRunRecovery(Boolean missedRunRecovery) { this.missedRunRecovery = missedRunRecovery; }
    public Long getSupersedesSnapshotId() { return supersedesSnapshotId; }
    public void setSupersedesSnapshotId(Long supersedesSnapshotId) { this.supersedesSnapshotId = supersedesSnapshotId; }
}
