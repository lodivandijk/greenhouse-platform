package com.greenhouse.observation;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final Clock clock = Clock.systemUTC();

    public ObservationService(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    public ObservationStatus record(ObservationRequest request) {
        ObservationEntity entity = new ObservationEntity(
                null,
                request.deviceId(),
                request.temperatureCelsius(),
                request.humidityPercent(),
                request.pressureHpa(),
                clock.instant()
        );

        return toStatus(observationRepository.save(entity));
    }

    public ObservationStatus getLatest(String deviceId) {
        return observationRepository.findFirstByDeviceIdOrderByReceivedAtDesc(deviceId)
                .map(this::toStatus)
                .orElseThrow(() -> new ObservationNotFoundException(deviceId));
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
