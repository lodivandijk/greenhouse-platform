package com.greenhouse.observation;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ObservationStore {

    private final ConcurrentMap<String, StoredObservation> observations = new ConcurrentHashMap<>();
    private final Clock clock;

    public ObservationStore() {
        this(Clock.systemUTC());
    }

    ObservationStore(Clock clock) {
        this.clock = clock;
    }

    public ObservationStatus record(ObservationRequest request) {
        Instant receivedAt = clock.instant();

        StoredObservation stored = new StoredObservation(
                request.deviceId(),
                request.temperatureCelsius(),
                request.humidityPercent(),
                request.pressureHpa(),
                receivedAt
        );

        observations.put(request.deviceId(), stored);

        return toStatus(stored);
    }

    public ObservationStatus getLatest(String deviceId) {
        StoredObservation stored = observations.get(deviceId);

        if (stored == null) {
            throw new ObservationNotFoundException(deviceId);
        }

        return toStatus(stored);
    }

    private ObservationStatus toStatus(StoredObservation observation) {
        return new ObservationStatus(
                observation.deviceId(),
                observation.temperatureCelsius(),
                observation.humidityPercent(),
                observation.pressureHpa(),
                observation.receivedAt()
        );
    }

    private record StoredObservation(
            String deviceId,
            Double temperatureCelsius,
            Double humidityPercent,
            Double pressureHpa,
            Instant receivedAt
    ) {
    }
}
