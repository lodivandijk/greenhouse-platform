package com.greenhouse.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "device")
public class DeviceEntity {

    @Id
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "software_version")
    private String softwareVersion;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_ip_address")
    private String lastIpAddress;

    @Column(name = "last_signal_strength_dbm")
    private Integer lastSignalStrengthDbm;

    @Column(name = "last_uptime_seconds")
    private Long lastUptimeSeconds;

    @Column(name = "heartbeat_count")
    private long heartbeatCount;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DeviceEntity() {
    }

    public DeviceEntity(
            String deviceId,
            String softwareVersion,
            Instant firstSeenAt,
            Instant lastSeenAt,
            String lastIpAddress,
            Integer lastSignalStrengthDbm,
            Long lastUptimeSeconds,
            long heartbeatCount,
            boolean enabled,
            Instant updatedAt
    ) {
        this.deviceId = deviceId;
        this.softwareVersion = softwareVersion;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.lastIpAddress = lastIpAddress;
        this.lastSignalStrengthDbm = lastSignalStrengthDbm;
        this.lastUptimeSeconds = lastUptimeSeconds;
        this.heartbeatCount = heartbeatCount;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getLastIpAddress() {
        return lastIpAddress;
    }

    public void setLastIpAddress(String lastIpAddress) {
        this.lastIpAddress = lastIpAddress;
    }

    public Integer getLastSignalStrengthDbm() {
        return lastSignalStrengthDbm;
    }

    public void setLastSignalStrengthDbm(Integer lastSignalStrengthDbm) {
        this.lastSignalStrengthDbm = lastSignalStrengthDbm;
    }

    public Long getLastUptimeSeconds() {
        return lastUptimeSeconds;
    }

    public void setLastUptimeSeconds(Long lastUptimeSeconds) {
        this.lastUptimeSeconds = lastUptimeSeconds;
    }

    public long getHeartbeatCount() {
        return heartbeatCount;
    }

    public void setHeartbeatCount(long heartbeatCount) {
        this.heartbeatCount = heartbeatCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
