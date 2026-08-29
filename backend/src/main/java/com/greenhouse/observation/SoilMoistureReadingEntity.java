package com.greenhouse.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "soil_moisture_reading")
public class SoilMoistureReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "raw_adc")
    private Integer rawAdc;

    @Column(name = "millivolts")
    private Double millivolts;

    @Column(name = "received_at")
    private Instant receivedAt;

    public SoilMoistureReadingEntity() {
    }

    public SoilMoistureReadingEntity(
            Long id,
            String deviceId,
            String sensorId,
            Integer rawAdc,
            Double millivolts,
            Instant receivedAt
    ) {
        this.id = id;
        this.deviceId = deviceId;
        this.sensorId = sensorId;
        this.rawAdc = rawAdc;
        this.millivolts = millivolts;
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

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public Integer getRawAdc() {
        return rawAdc;
    }

    public void setRawAdc(Integer rawAdc) {
        this.rawAdc = rawAdc;
    }

    public Double getMillivolts() {
        return millivolts;
    }

    public void setMillivolts(Double millivolts) {
        this.millivolts = millivolts;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
