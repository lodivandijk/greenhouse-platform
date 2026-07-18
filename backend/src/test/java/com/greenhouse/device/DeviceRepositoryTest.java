package com.greenhouse.device;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceRepositoryTest {

    @Autowired
    private DeviceRepository deviceRepository;

    private static String uniqueDeviceId() {
        return "test-device-" + UUID.randomUUID();
    }

    @Test
    void savesAndFindsDeviceById() {
        String deviceId = uniqueDeviceId();
        Instant now = Instant.parse("2099-01-01T00:00:00Z");

        deviceRepository.save(new DeviceEntity(
                deviceId, "0.1.0", now, now, "192.168.1.68", -52, 120L, 1, true, now
        ));

        Optional<DeviceEntity> found = deviceRepository.findById(deviceId);

        assertThat(found).isPresent();
        assertThat(found.get().getSoftwareVersion()).isEqualTo("0.1.0");
        assertThat(found.get().getHeartbeatCount()).isEqualTo(1);
        assertThat(found.get().isEnabled()).isTrue();
    }

    @Test
    void returnsEmptyForUnknownDevice() {
        Optional<DeviceEntity> found = deviceRepository.findById(uniqueDeviceId());

        assertThat(found).isEmpty();
    }

    @Test
    void updatingAnExistingDeviceOverwritesItsRow() {
        String deviceId = uniqueDeviceId();
        Instant firstSeen = Instant.parse("2099-02-01T00:00:00Z");
        Instant secondSeen = Instant.parse("2099-02-01T01:00:00Z");

        deviceRepository.save(new DeviceEntity(
                deviceId, "0.1.0", firstSeen, firstSeen, "192.168.1.68", -52, 60L, 1, true, firstSeen
        ));

        DeviceEntity existing = deviceRepository.findById(deviceId).orElseThrow();
        existing.setLastSeenAt(secondSeen);
        existing.setHeartbeatCount(existing.getHeartbeatCount() + 1);
        existing.setUpdatedAt(secondSeen);
        deviceRepository.save(existing);

        DeviceEntity updated = deviceRepository.findById(deviceId).orElseThrow();

        assertThat(updated.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(updated.getLastSeenAt()).isEqualTo(secondSeen);
        assertThat(updated.getHeartbeatCount()).isEqualTo(2);
        assertThat(deviceRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void findAllIncludesSavedDevices() {
        String deviceId = uniqueDeviceId();
        Instant now = Instant.parse("2099-03-01T00:00:00Z");

        deviceRepository.save(new DeviceEntity(
                deviceId, "0.1.0", now, now, "192.168.1.68", -52, 60L, 1, true, now
        ));

        List<DeviceEntity> all = deviceRepository.findAll();

        assertThat(all).extracting(DeviceEntity::getDeviceId).contains(deviceId);
    }
}
