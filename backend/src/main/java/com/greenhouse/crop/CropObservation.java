package com.greenhouse.crop;

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
@Table(name = "crop_observation")
public class CropObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "crop_id")
    private Long cropId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric")
    private CropObservationMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type")
    private CropObservationValueType valueType;

    @Column(name = "numeric_value")
    private Double numericValue;

    @Column(name = "text_value")
    private String textValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "unit")
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private CropObservationSource source;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "notes")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json")
    private Map<String, Object> metadata;

    @Column(name = "created_at")
    private Instant createdAt;

    public CropObservation() {
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

    public CropObservationMetric getMetric() {
        return metric;
    }

    public void setMetric(CropObservationMetric metric) {
        this.metric = metric;
    }

    public CropObservationValueType getValueType() {
        return valueType;
    }

    public void setValueType(CropObservationValueType valueType) {
        this.valueType = valueType;
    }

    public Double getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(Double numericValue) {
        this.numericValue = numericValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public CropObservationSource getSource() {
        return source;
    }

    public void setSource(CropObservationSource source) {
        this.source = source;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
