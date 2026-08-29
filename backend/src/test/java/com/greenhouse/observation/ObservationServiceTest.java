package com.greenhouse.observation;

import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No @Transactional wrapper here deliberately: ObservationService.record is
// itself @Transactional, and this test needs to observe what is (and isn't)
// actually committed after that transaction rolls back - joining it into an
// outer test transaction would mask that. See McpServerIntegrationTest for
// the same real-transaction-boundary reasoning elsewhere in this codebase.
// Consequence: writes here genuinely persist, so every deviceId/sensorId used
// by a test that expects success must be tracked and cleaned up below.
@SpringBootTest(properties = "greenhouse.evaluation.enabled=false")
class ObservationServiceTest {

    @Autowired
    private ObservationService observationService;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private SoilMoistureReadingRepository soilMoistureReadingRepository;

    private final List<String> createdDeviceIds = new ArrayList<>();
    private final List<String> createdSensorIds = new ArrayList<>();

    private String uniqueDeviceId() {
        String deviceId = "test-device-" + UUID.randomUUID();
        createdDeviceIds.add(deviceId);
        return deviceId;
    }

    private String uniqueSensorId() {
        String sensorId = "test-sensor-" + UUID.randomUUID();
        createdSensorIds.add(sensorId);
        return sensorId;
    }

    @AfterEach
    void cleanUpCreatedRows() {
        createdSensorIds.forEach(sensorId ->
                soilMoistureReadingRepository.deleteAll(
                        soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensorId)));
        createdDeviceIds.forEach(deviceId ->
                observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)
                        .ifPresent(observationRepository::delete));
    }

    @Test
    void recordsBmeOnlyPayloadWithNoSoilReadings() {
        String deviceId = uniqueDeviceId();

        ObservationStatus status = observationService.record(
                new ObservationRequest(deviceId, 21.0, 55.0, 1010.0)
        );

        assertThat(status.deviceId()).isEqualTo(deviceId);
        assertThat(soilMoistureReadingRepository.findAllByOrderByReceivedAtDesc()).noneMatch(
                reading -> reading.getDeviceId().equals(deviceId)
        );
    }

    @Test
    void recordsThreeSoilReadingsInOnePayloadIndependently() {
        String deviceId = uniqueDeviceId();
        String sensor1 = uniqueSensorId();
        String sensor2 = uniqueSensorId();
        String sensor3 = uniqueSensorId();

        observationService.record(new ObservationRequest(
                deviceId, 21.0, 55.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(sensor1, 2870),
                        new SoilMoistureReadingRequest(sensor2, 2915),
                        new SoilMoistureReadingRequest(sensor3, 2842)
                )
        ));

        assertThat(soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(sensor1))
                .isPresent().get().extracting(SoilMoistureReadingEntity::getRawAdc).isEqualTo(2870);
        assertThat(soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(sensor2))
                .isPresent().get().extracting(SoilMoistureReadingEntity::getRawAdc).isEqualTo(2915);
        assertThat(soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(sensor3))
                .isPresent().get().extracting(SoilMoistureReadingEntity::getRawAdc).isEqualTo(2842);
    }

    @Test
    void acceptsBoundaryAdcValuesZeroAndFourThousandNinetyFive() {
        String deviceId = uniqueDeviceId();
        String dryEnd = uniqueSensorId();
        String wetEnd = uniqueSensorId();

        observationService.record(new ObservationRequest(
                deviceId, 21.0, 55.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(dryEnd, 4095),
                        new SoilMoistureReadingRequest(wetEnd, 0)
                )
        ));

        assertThat(soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(dryEnd))
                .isPresent().get().extracting(SoilMoistureReadingEntity::getRawAdc).isEqualTo(4095);
        assertThat(soilMoistureReadingRepository.findFirstBySensorIdOrderByReceivedAtDesc(wetEnd))
                .isPresent().get().extracting(SoilMoistureReadingEntity::getRawAdc).isEqualTo(0);
    }

    @Test
    void rejectsDuplicateSensorIdsWithinOnePayloadAndPersistsNothing() {
        String deviceId = uniqueDeviceId();
        String sensorId = uniqueSensorId();

        assertThatThrownBy(() -> observationService.record(new ObservationRequest(
                deviceId, 21.0, 55.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(sensorId, 2870),
                        new SoilMoistureReadingRequest(sensorId, 2900)
                )
        ))).isInstanceOf(DomainValidationException.class);

        assertThat(observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)).isEmpty();
        assertThat(soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensorId)).isEmpty();
    }

    @Test
    void transactionFailureMidBatchLeavesNothingPersisted() {
        String deviceId = uniqueDeviceId();
        String validSensor = uniqueSensorId();
        String invalidSensor = uniqueSensorId();

        // Bean validation (@Min/@Max) only runs behind @Valid on the REST path,
        // so constructing the out-of-range request directly here is the only
        // way to reach the database's own CHECK constraint and prove the
        // @Transactional rollback actually works, not just that validation
        // caught it upstream.
        assertThatThrownBy(() -> observationService.record(new ObservationRequest(
                deviceId, 21.0, 55.0, 1010.0,
                List.of(
                        new SoilMoistureReadingRequest(validSensor, 2870),
                        new SoilMoistureReadingRequest(invalidSensor, 9999)
                )
        ))).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)).isEmpty();
        assertThat(soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(validSensor)).isEmpty();
    }
}
