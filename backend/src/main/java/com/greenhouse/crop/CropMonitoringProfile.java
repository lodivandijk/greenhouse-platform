package com.greenhouse.crop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

// Versioned interpretation profile for one crop: the preferred temperature
// range, how long an excursion must persist before it is actionable, and how
// the crop's soil should be managed.
//
// A change creates a new version rather than editing this row, so a historical
// assessment always references the profile that actually produced it (ADR-021).
@Entity
@Table(name = "crop_monitoring_profile")
public class CropMonitoringProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "crop_id")
    private Long cropId;

    @Column(name = "version")
    private Integer version;

    @Column(name = "preferred_temperature_min_celsius")
    private Double preferredTemperatureMinCelsius;

    @Column(name = "preferred_temperature_max_celsius")
    private Double preferredTemperatureMaxCelsius;

    @Column(name = "temperature_excursion_seconds")
    private Long temperatureExcursionSeconds;

    @Column(name = "temperature_recovery_seconds")
    private Long temperatureRecoverySeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_moisture_strategy")
    private SoilMoistureStrategy soilMoistureStrategy;

    @Column(name = "soil_dry_threshold_index")
    private Double soilDryThresholdIndex;

    @Column(name = "soil_wet_threshold_index")
    private Double soilWetThresholdIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_monitoring_mode")
    private SoilMonitoringMode soilMonitoringMode;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "source_notes")
    private String sourceNotes;

    @Column(name = "supersedes_profile_id")
    private Long supersedesProfileId;

    public CropMonitoringProfile() {
    }

    public Duration temperatureExcursionDuration() {
        return Duration.ofSeconds(temperatureExcursionSeconds);
    }

    public Duration temperatureRecoveryDuration() {
        return Duration.ofSeconds(temperatureRecoverySeconds);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCropId() {
        return cropId;
    }

    public void setCropId(Long cropId) {
        this.cropId = cropId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Double getPreferredTemperatureMinCelsius() {
        return preferredTemperatureMinCelsius;
    }

    public void setPreferredTemperatureMinCelsius(Double preferredTemperatureMinCelsius) {
        this.preferredTemperatureMinCelsius = preferredTemperatureMinCelsius;
    }

    public Double getPreferredTemperatureMaxCelsius() {
        return preferredTemperatureMaxCelsius;
    }

    public void setPreferredTemperatureMaxCelsius(Double preferredTemperatureMaxCelsius) {
        this.preferredTemperatureMaxCelsius = preferredTemperatureMaxCelsius;
    }

    public Long getTemperatureExcursionSeconds() {
        return temperatureExcursionSeconds;
    }

    public void setTemperatureExcursionSeconds(Long temperatureExcursionSeconds) {
        this.temperatureExcursionSeconds = temperatureExcursionSeconds;
    }

    public Long getTemperatureRecoverySeconds() {
        return temperatureRecoverySeconds;
    }

    public void setTemperatureRecoverySeconds(Long temperatureRecoverySeconds) {
        this.temperatureRecoverySeconds = temperatureRecoverySeconds;
    }

    public SoilMoistureStrategy getSoilMoistureStrategy() {
        return soilMoistureStrategy;
    }

    public void setSoilMoistureStrategy(SoilMoistureStrategy soilMoistureStrategy) {
        this.soilMoistureStrategy = soilMoistureStrategy;
    }

    public Double getSoilDryThresholdIndex() {
        return soilDryThresholdIndex;
    }

    public void setSoilDryThresholdIndex(Double soilDryThresholdIndex) {
        this.soilDryThresholdIndex = soilDryThresholdIndex;
    }

    public Double getSoilWetThresholdIndex() {
        return soilWetThresholdIndex;
    }

    public void setSoilWetThresholdIndex(Double soilWetThresholdIndex) {
        this.soilWetThresholdIndex = soilWetThresholdIndex;
    }

    public SoilMonitoringMode getSoilMonitoringMode() {
        return soilMonitoringMode;
    }

    public void setSoilMonitoringMode(SoilMonitoringMode soilMonitoringMode) {
        this.soilMonitoringMode = soilMonitoringMode;
    }

    // Older rows predate the column's introduction and read as SENSOR, which is
    // the behaviour they were written under (ADR-024).
    public boolean isManuallyMonitored() {
        return soilMonitoringMode == SoilMonitoringMode.MANUAL;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getSourceNotes() {
        return sourceNotes;
    }

    public void setSourceNotes(String sourceNotes) {
        this.sourceNotes = sourceNotes;
    }

    public Long getSupersedesProfileId() {
        return supersedesProfileId;
    }

    public void setSupersedesProfileId(Long supersedesProfileId) {
        this.supersedesProfileId = supersedesProfileId;
    }
}
