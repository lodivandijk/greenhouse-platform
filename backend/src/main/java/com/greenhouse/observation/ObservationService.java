package com.greenhouse.observation;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final SoilMoistureReadingRepository soilMoistureReadingRepository;
    private final Clock clock = Clock.systemUTC();

    public ObservationService(
            ObservationRepository observationRepository,
            SoilMoistureReadingRepository soilMoistureReadingRepository
    ) {
        this.observationRepository = observationRepository;
        this.soilMoistureReadingRepository = soilMoistureReadingRepository;
    }

    // One observation envelope produces one BME reading and zero or more soil
    // readings together; @Transactional so a bad soil reading never leaves the
    // BME reading (or a partial set of soil readings) committed on its own.
    @Transactional
    public ObservationStatus record(ObservationRequest request) {
        List<SoilMoistureReadingRequest> soilReadings = request.soilMoistureOrEmpty();
        validateNoDuplicateSensorIds(soilReadings);

        Instant receivedAt = clock.instant();

        ObservationEntity entity = new ObservationEntity(
                null,
                request.deviceId(),
                request.temperatureCelsius(),
                request.humidityPercent(),
                request.pressureHpa(),
                receivedAt
        );
        ObservationStatus status = toStatus(observationRepository.save(entity));

        for (SoilMoistureReadingRequest reading : soilReadings) {
            soilMoistureReadingRepository.save(new SoilMoistureReadingEntity(
                    null,
                    request.deviceId(),
                    reading.sensorId(),
                    reading.rawAdc(),
                    null,
                    receivedAt
            ));
        }

        return status;
    }

    private void validateNoDuplicateSensorIds(List<SoilMoistureReadingRequest> soilReadings) {
        Set<String> seen = new HashSet<>();
        for (SoilMoistureReadingRequest reading : soilReadings) {
            if (!seen.add(reading.sensorId())) {
                throw new DomainValidationException(
                        "Duplicate sensorId in one observation payload: " + reading.sensorId());
            }
        }
    }

    public ObservationStatus getLatest(String deviceId) {
        return observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)
                .map(this::toStatus)
                .orElseThrow(() -> new ObservationNotFoundException(deviceId));
    }

    public Optional<ObservationStatus> findLatestForDevice(String deviceId) {
        return observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)
                .map(this::toStatus);
    }

    public ObservationStatus getLatest() {
        return observationRepository.findFirstByOrderByReceivedAtDesc()
                .map(this::toStatus)
                .orElseThrow(ObservationNotFoundException::new);
    }

    public List<ObservationStatus> getAll() {
        return observationRepository.findAllByOrderByReceivedAtDesc().stream()
                .map(this::toStatus)
                .toList();
    }

    public List<SoilMoistureReadingResponse> getAllSoilMoistureReadings() {
        return soilMoistureReadingRepository.findAllByOrderByReceivedAtDesc().stream()
                .map(this::toSoilMoistureResponse)
                .toList();
    }

    public List<SoilMoistureReadingResponse> getSoilMoistureReadingsForSensor(String sensorId) {
        return soilMoistureReadingRepository.findAllBySensorIdOrderByReceivedAtDesc(sensorId).stream()
                .map(this::toSoilMoistureResponse)
                .toList();
    }

    private SoilMoistureReadingResponse toSoilMoistureResponse(SoilMoistureReadingEntity entity) {
        return new SoilMoistureReadingResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getSensorId(),
                entity.getRawAdc(),
                entity.getMillivolts(),
                entity.getReceivedAt()
        );
    }

    private ObservationStatus toStatus(ObservationEntity entity) {
        return new ObservationStatus(
                entity.getDeviceId(),
                entity.getTemperatureCelsius(),
                entity.getHumidityPercent(),
                entity.getPressureHpa(),
                entity.getReceivedAt()
        );
    }
}
