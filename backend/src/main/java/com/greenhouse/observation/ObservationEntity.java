package com.greenhouse.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "observation")
public class ObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "humidity_percent")
    private Double humidityPercent;

    @Column(name = "pressure_hpa")
    private Double pressureHpa;

    @Column(name = "received_at")
    private Instant receivedAt;

    public ObservationEntity() {
    }

    public ObservationEntity(
            Long id,
            String deviceId,
            Double temperatureCelsius,
            Double humidityPercent,
            Double pressureHpa,
            Instant receivedAt
    ) {
        this.id = id;
        this.deviceId = deviceId;
        this.temperatureCelsius = temperatureCelsius;
        this.humidityPercent = humidityPercent;
        this.pressureHpa = pressureHpa;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(Double temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public Double getHumidityPercent() {
        return humidityPercent;
    }

    public void setHumidityPercent(Double humidityPercent) {
        this.humidityPercent = humidityPercent;
    }

    public Double getPressureHpa() {
        return pressureHpa;
    }

    public void setPressureHpa(Double pressureHpa) {
        this.pressureHpa = pressureHpa;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
