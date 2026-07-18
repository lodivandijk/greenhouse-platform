package com.greenhouse.observation;

public class ObservationNotFoundException extends RuntimeException {

    public ObservationNotFoundException(String deviceId) {
        super("No observation recorded for device: " + deviceId);
    }
}
