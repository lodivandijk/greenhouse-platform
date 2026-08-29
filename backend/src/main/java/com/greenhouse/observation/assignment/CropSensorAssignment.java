package com.greenhouse.observation.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Which crop a physical probe currently serves. Separate from SensorCalibration
// because sensor identity and crop assignment change independently (ADR-022).
//
// The absence of a row for a sensor, or of any row for a crop, is meaningful:
// it is how "no sensor assigned" is represented, which lets the assessment
// engine say NO_SENSOR_ASSIGNED rather than inferring anything from silence.
@Entity
@Table(name = "crop_sensor_assignment")
public class CropSensorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "crop_id")
    private Long cropId;

    @Column(name = "version")
    private Integer version;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "notes")
    private String notes;

    @Column(name = "supersedes_assignment_id")
    private Long supersedesAssignmentId;

    public CropSensorAssignment() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public Long getCropId() { return cropId; }
    public void setCropId(Long cropId) { this.cropId = cropId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getSupersedesAssignmentId() { return supersedesAssignmentId; }
    public void setSupersedesAssignmentId(Long supersedesAssignmentId) { this.supersedesAssignmentId = supersedesAssignmentId; }
}
