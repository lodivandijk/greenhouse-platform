package com.greenhouse.crop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "harvest")
public class Harvest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "crop_id")
    private Long cropId;

    @Column(name = "harvested_at")
    private Instant harvestedAt;

    @Column(name = "quantity")
    private Double quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit")
    private HarvestUnit unit;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    public Harvest() {
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

    public Instant getHarvestedAt() {
        return harvestedAt;
    }

    public void setHarvestedAt(Instant harvestedAt) {
        this.harvestedAt = harvestedAt;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public HarvestUnit getUnit() {
        return unit;
    }

    public void setUnit(HarvestUnit unit) {
        this.unit = unit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
