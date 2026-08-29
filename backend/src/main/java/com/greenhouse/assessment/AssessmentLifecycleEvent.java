package com.greenhouse.assessment;

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

// One append-only row per assessment transition, carrying a FULL snapshot of
// the assessment as it stood at that moment rather than a diff. That is what
// makes the mutable `assessment` row a genuine rebuildable projection rather
// than an unlogged source of truth (ADR-021).
@Entity
@Table(name = "assessment_lifecycle_event")
public class AssessmentLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private AssessmentLifecycleEventType eventType;

    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "greenhouse_id")
    private String greenhouseId;

    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "crop_id")
    private Long cropId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type")
    private AssessmentScopeType scopeType;

    @Column(name = "scope_id")
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "code")
    private AssessmentCode code;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private AssessmentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AssessmentStatus status;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json")
    private Map<String, Object> evidence;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "rule_version")
    private int ruleVersion;

    @Column(name = "monitoring_profile_id")
    private Long monitoringProfileId;

    @Column(name = "monitoring_profile_version")
    private Integer monitoringProfileVersion;

    @Column(name = "calibration_id")
    private Long calibrationId;

    @Column(name = "calibration_version")
    private Integer calibrationVersion;

    @Column(name = "first_detected_at")
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at")
    private Instant lastDetectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private ActorType actorType;

    @Column(name = "actor_id")
    private String actorId;

    public AssessmentLifecycleEvent() {
    }

    // Captures the entity exactly as it stands, so replaying events in
    // occurredAt order reproduces the assessment table.
    public static AssessmentLifecycleEvent snapshotOf(
            AssessmentEntity entity,
            AssessmentLifecycleEventType eventType,
            Instant occurredAt,
            ActorType actorType
    ) {
        AssessmentLifecycleEvent event = new AssessmentLifecycleEvent();
        event.assessmentId = entity.getId();
        event.eventType = eventType;
        event.correlationKey = entity.getCorrelationKey();
        event.greenhouseId = entity.getGreenhouseId();
        event.zoneId = entity.getZoneId();
        event.deviceId = entity.getDeviceId();
        event.cropId = entity.getCropId();
        event.scopeType = entity.getScopeType();
        event.scopeId = entity.getScopeId();
        event.code = entity.getCode();
        event.severity = entity.getSeverity();
        event.status = entity.getStatus();
        event.message = entity.getMessage();
        event.evidence = entity.getEvidence();
        event.ruleId = entity.getRuleId();
        event.ruleVersion = entity.getRuleVersion();
        event.monitoringProfileId = entity.getMonitoringProfileId();
        event.monitoringProfileVersion = entity.getMonitoringProfileVersion();
        event.calibrationId = entity.getCalibrationId();
        event.calibrationVersion = entity.getCalibrationVersion();
        event.firstDetectedAt = entity.getFirstDetectedAt();
        event.lastDetectedAt = entity.getLastDetectedAt();
        event.resolvedAt = entity.getResolvedAt();
        event.occurredAt = occurredAt;
        event.actorType = actorType;
        return event;
    }

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public AssessmentLifecycleEventType getEventType() { return eventType; }
    public String getCorrelationKey() { return correlationKey; }
    public String getGreenhouseId() { return greenhouseId; }
    public String getZoneId() { return zoneId; }
    public String getDeviceId() { return deviceId; }
    public Long getCropId() { return cropId; }
    public AssessmentScopeType getScopeType() { return scopeType; }
    public String getScopeId() { return scopeId; }
    public AssessmentCode getCode() { return code; }
    public AssessmentSeverity getSeverity() { return severity; }
    public AssessmentStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Map<String, Object> getEvidence() { return evidence; }
    public String getRuleId() { return ruleId; }
    public int getRuleVersion() { return ruleVersion; }
    public Long getMonitoringProfileId() { return monitoringProfileId; }
    public Integer getMonitoringProfileVersion() { return monitoringProfileVersion; }
    public Long getCalibrationId() { return calibrationId; }
    public Integer getCalibrationVersion() { return calibrationVersion; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastDetectedAt() { return lastDetectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getOccurredAt() { return occurredAt; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
}
