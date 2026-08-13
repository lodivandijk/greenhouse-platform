package com.greenhouse.crop;

public class CropObservationNotFoundException extends RuntimeException {

    public CropObservationNotFoundException(Long observationId) {
        super("Crop observation not found: " + observationId);
    }
}
