package com.greenhouse.observation;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observations")
public class ObservationController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ObservationController.class);

    private final ObservationStore observationStore;

    public ObservationController(ObservationStore observationStore) {
        this.observationStore = observationStore;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ObservationStatus receiveObservation(
            @Valid @RequestBody ObservationRequest request
    ) {
        ObservationStatus status = observationStore.record(request);

        LOGGER.info(
                "Observation received: deviceId={}, temperatureCelsius={}, humidityPercent={}, pressureHpa={}",
                status.deviceId(),
                status.temperatureCelsius(),
                status.humidityPercent(),
                status.pressureHpa()
        );

        return status;
    }

    @GetMapping("/{deviceId}")
    public ObservationStatus getLatest(@PathVariable String deviceId) {
        return observationStore.getLatest(deviceId);
    }
}
