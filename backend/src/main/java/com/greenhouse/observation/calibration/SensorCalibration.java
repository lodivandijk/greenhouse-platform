package com.greenhouse.observation.calibration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Dry/wet reference points for one physical probe. Belongs to the probe, not
// the crop - a probe moved to a different pot keeps its calibration (ADR-022).
//
// Versioned: recalibrating creates a new row so that a historical assessment
// still resolves the calibration that actually produced its moisture index.
@Entity
@Table(name = "sensor_calibration")
public class SensorCalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "version")
    private Integer version;

    @Column(name = "dry_reference_raw")
    private Integer dryReferenceRaw;

    @Column(name = "wet_reference_raw")
    private Integer wetReferenceRaw;

    @Column(name = "calibrated_at")
    private Instant calibratedAt;

    @Column(name = "calibrated_by")
    private String calibratedBy;

    @Column(name = "method")
    private String method;

    @Column(name = "notes")
    private String notes;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "supersedes_calibration_id")
    private Long supersedesCalibrationId;

    public SensorCalibration() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getDryReferenceRaw() { return dryReferenceRaw; }
    public void setDryReferenceRaw(Integer dryReferenceRaw) { this.dryReferenceRaw = dryReferenceRaw; }
    public Integer getWetReferenceRaw() { return wetReferenceRaw; }
    public void setWetReferenceRaw(Integer wetReferenceRaw) { this.wetReferenceRaw = wetReferenceRaw; }
    public Instant getCalibratedAt() { return calibratedAt; }
    public void setCalibratedAt(Instant calibratedAt) { this.calibratedAt = calibratedAt; }
    public String getCalibratedBy() { return calibratedBy; }
    public void setCalibratedBy(String calibratedBy) { this.calibratedBy = calibratedBy; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public Long getSupersedesCalibrationId() { return supersedesCalibrationId; }
    public void setSupersedesCalibrationId(Long supersedesCalibrationId) { this.supersedesCalibrationId = supersedesCalibrationId; }
}
