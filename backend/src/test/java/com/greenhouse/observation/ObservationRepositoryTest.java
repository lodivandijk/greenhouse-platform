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
class ObservationRepositoryTest {

    @Autowired
    private ObservationRepository observationRepository;

    private static String uniqueDeviceId() {
        return "test-device-" + UUID.randomUUID();
    }

    @Test
    void findsMostRecentObservationForADevice() {
        String deviceId = uniqueDeviceId();
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-01-01T00:05:00Z");

        observationRepository.save(
                new ObservationEntity(null, deviceId, 20.0, 50.0, 1000.0, older)
        );
        observationRepository.save(
                new ObservationEntity(null, deviceId, 21.0, 51.0, 1001.0, newer)
        );

        Optional<ObservationEntity> latest =
                observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId);

        assertThat(latest).isPresent();
        assertThat(latest.get().getReceivedAt()).isEqualTo(newer);
        assertThat(latest.get().getTemperatureCelsius()).isEqualTo(21.0);
    }

    @Test
    void returnsEmptyWhenDeviceHasNoObservations() {
        Optional<ObservationEntity> latest =
                observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(uniqueDeviceId());

        assertThat(latest).isEmpty();
    }

    @Test
    void findsGlobalMostRecentObservationAcrossDevices() {
        // Far-future timestamps guarantee these rows outrank any real data
        // already sitting in the shared dev database from manual testing.
        String deviceA = uniqueDeviceId();
        String deviceB = uniqueDeviceId();
        Instant older = Instant.parse("2099-01-01T00:00:00Z");
        Instant newer = Instant.parse("2099-01-01T01:00:00Z");

        observationRepository.save(
                new ObservationEntity(null, deviceA, 20.0, 50.0, 1000.0, older)
        );
        observationRepository.save(
                new ObservationEntity(null, deviceB, 22.0, 52.0, 1002.0, newer)
        );

        Optional<ObservationEntity> latest = observationRepository.findFirstByOrderByReceivedAtDesc();

        assertThat(latest).isPresent();
        assertThat(latest.get().getDeviceId()).isEqualTo(deviceB);
    }

    @Test
    void listsAllObservationsOrderedByMostRecentFirst() {
        String deviceId = uniqueDeviceId();
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        Instant second = Instant.parse("2026-03-01T00:10:00Z");
        Instant third = Instant.parse("2026-03-01T00:20:00Z");

        observationRepository.save(
                new ObservationEntity(null, deviceId, 18.0, 40.0, 990.0, second)
        );
        observationRepository.save(
                new ObservationEntity(null, deviceId, 19.0, 41.0, 991.0, third)
        );
        observationRepository.save(
                new ObservationEntity(null, deviceId, 17.0, 39.0, 989.0, first)
        );

        List<ObservationEntity> forThisDevice = observationRepository.findAllByOrderByReceivedAtDesc()
                .stream()
                .filter(observation -> observation.getDeviceId().equals(deviceId))
                .toList();

        assertThat(forThisDevice)
                .extracting(ObservationEntity::getReceivedAt)
                .containsExactly(third, second, first);
    }
}
