package com.greenhouse.observation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SoilMoistureReadingRepositoryTest {

    @Autowired
    private SoilMoistureReadingRepository soilMoistureReadingRepository;

    private static String uniqueSensorId() {
        return "test-sensor-" + UUID.randomUUID();
    }

    @Test
    void savesAndRoundTripsAReading() {
        String sensorId = uniqueSensorId();
        Instant receivedAt = Instant.parse("2026-08-29T09:00:00Z");

        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, "greenhouse-esp32-01", sensorId, 2870, null, receivedAt)
        );

        List<SoilMoistureReadingEntity> readings =
                soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensorId);

        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).getDeviceId()).isEqualTo("greenhouse-esp32-01");
        assertThat(readings.get(0).getRawAdc()).isEqualTo(2870);
        assertThat(readings.get(0).getMillivolts()).isNull();
        assertThat(readings.get(0).getReceivedAt()).isEqualTo(receivedAt);
    }

    @Test
    void findsMostRecentReadingForASensor() {
        String sensorId = uniqueSensorId();
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-01-01T00:05:00Z");

        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, "greenhouse-esp32-01", sensorId, 2800, null, older)
        );
        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, "greenhouse-esp32-01", sensorId, 1200, null, newer)
        );

        Optional<SoilMoistureReadingEntity> latest =
                soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(sensorId);

        assertThat(latest).isPresent();
        assertThat(latest.get().getRawAdc()).isEqualTo(1200);
        assertThat(latest.get().getReceivedAt()).isEqualTo(newer);
    }

    @Test
    void returnsEmptyWhenSensorHasNoReadings() {
        List<SoilMoistureReadingEntity> readings =
                soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(uniqueSensorId());

        assertThat(readings).isEmpty();
    }

    @Test
    void threeSensorsInOneCyclePersistIndependently() {
        String deviceId = "test-device-" + UUID.randomUUID();
        String sensor1 = uniqueSensorId();
        String sensor2 = uniqueSensorId();
        String sensor3 = uniqueSensorId();
        Instant receivedAt = Instant.parse("2026-08-29T09:00:00Z");

        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, deviceId, sensor1, 2870, null, receivedAt));
        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, deviceId, sensor2, 2915, null, receivedAt));
        soilMoistureReadingRepository.save(
                new SoilMoistureReadingEntity(null, deviceId, sensor3, 2842, null, receivedAt));

        assertThat(soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensor1))
                .extracting(SoilMoistureReadingEntity::getRawAdc).containsExactly(2870);
        assertThat(soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensor2))
                .extracting(SoilMoistureReadingEntity::getRawAdc).containsExactly(2915);
        assertThat(soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensor3))
                .extracting(SoilMoistureReadingEntity::getRawAdc).containsExactly(2842);
    }
}
