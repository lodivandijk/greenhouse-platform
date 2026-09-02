package com.greenhouse.observation;

import com.greenhouse.common.security.DeviceIdentityGuard;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

@RestController
@RequestMapping({"/api/observations", "/api/v1/observations"})
public class ObservationController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ObservationController.class);

    private final ObservationService observationService;
    private final DeviceIdentityGuard deviceIdentityGuard;

    public ObservationController(
            ObservationService observationService, DeviceIdentityGuard deviceIdentityGuard) {
        this.observationService = observationService;
        this.deviceIdentityGuard = deviceIdentityGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ObservationStatus receiveObservation(
            @Valid @RequestBody ObservationRequest request,
            HttpServletRequest httpRequest
    ) {
        deviceIdentityGuard.verify(httpRequest, request.deviceId());

        ObservationStatus status = observationService.record(request);

        LOGGER.info(
                "Observation received: deviceId={}, temperatureCelsius={}, humidityPercent={}, pressureHpa={}",
                status.deviceId(),
                status.temperatureCelsius(),
                status.humidityPercent(),
                status.pressureHpa()
        );

        return status;
    }

    @GetMapping
    public List<ObservationStatus> getAllObservations() {
        return observationService.getAll();
    }

    @GetMapping("/latest")
    public ObservationStatus getLatestObservation() {
        return observationService.getLatest();
    }

    @GetMapping("/{deviceId}")
    public ObservationStatus getLatestForDevice(@PathVariable String deviceId) {
        return observationService.getLatest(deviceId);
    }
}
