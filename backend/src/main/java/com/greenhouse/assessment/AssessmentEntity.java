package com.greenhouse.assessment;

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

@Entity
@Table(name = "assessment")
public class AssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "greenhouse_id")
    private String greenhouseId;

    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "device_id")
    private String deviceId;

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

    @Column(name = "first_detected_at")
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at")
    private Instant lastDetectedAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Crop context, populated only by crop-aware rules. The four zone/device
    // rules leave these null and are unaffected. Recording which profile and
    // calibration VERSION produced an assessment is what stops a later
    // recalibration from silently rewriting historical evidence (ADR-021).
    @Column(name = "crop_id")
    private Long cropId;

    @Column(name = "monitoring_profile_id")
    private Long monitoringProfileId;

    @Column(name = "monitoring_profile_version")
    private Integer monitoringProfileVersion;

    @Column(name = "calibration_id")
    private Long calibrationId;

    @Column(name = "calibration_version")
    private Integer calibrationVersion;

    public AssessmentEntity() {
    }

    public AssessmentEntity(
            Long id,
            String correlationKey,
            String greenhouseId,
            String zoneId,
            String deviceId,
            AssessmentScopeType scopeType,
            String scopeId,
            AssessmentCode code,
            AssessmentSeverity severity,
            AssessmentStatus status,
            String message,
            Map<String, Object> evidence,
            String ruleId,
            int ruleVersion,
            Instant firstDetectedAt,
            Instant lastDetectedAt,
            Instant lastEvaluatedAt,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.correlationKey = correlationKey;
        this.greenhouseId = greenhouseId;
        this.zoneId = zoneId;
        this.deviceId = deviceId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.code = code;
        this.severity = severity;
        this.status = status;
        this.message = message;
        this.evidence = evidence;
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.firstDetectedAt = firstDetectedAt;
        this.lastDetectedAt = lastDetectedAt;
        this.lastEvaluatedAt = lastEvaluatedAt;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public String getGreenhouseId() {
        return greenhouseId;
    }

    public void setGreenhouseId(String greenhouseId) {
        this.greenhouseId = greenhouseId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public AssessmentScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(AssessmentScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public AssessmentCode getCode() {
        return code;
    }

    public void setCode(AssessmentCode code) {
        this.code = code;
    }

    public AssessmentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AssessmentSeverity severity) {
        this.severity = severity;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(int ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public Instant getFirstDetectedAt() {
        return firstDetectedAt;
    }

    public void setFirstDetectedAt(Instant firstDetectedAt) {
        this.firstDetectedAt = firstDetectedAt;
    }

    public Instant getLastDetectedAt() {
        return lastDetectedAt;
    }

    public void setLastDetectedAt(Instant lastDetectedAt) {
        this.lastDetectedAt = lastDetectedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Instant lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCropId() {
        return cropId;
    }

    public void setCropId(Long cropId) {
        this.cropId = cropId;
    }

    public Long getMonitoringProfileId() {
        return monitoringProfileId;
    }

    public void setMonitoringProfileId(Long monitoringProfileId) {
        this.monitoringProfileId = monitoringProfileId;
    }

    public Integer getMonitoringProfileVersion() {
        return monitoringProfileVersion;
    }

    public void setMonitoringProfileVersion(Integer monitoringProfileVersion) {
        this.monitoringProfileVersion = monitoringProfileVersion;
    }

    public Long getCalibrationId() {
        return calibrationId;
    }

    public void setCalibrationId(Long calibrationId) {
        this.calibrationId = calibrationId;
    }

    public Integer getCalibrationVersion() {
        return calibrationVersion;
    }

    public void setCalibrationVersion(Integer calibrationVersion) {
        this.calibrationVersion = calibrationVersion;
    }
}
