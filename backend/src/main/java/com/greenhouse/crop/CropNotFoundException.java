package com.greenhouse.crop;

public class CropNotFoundException extends RuntimeException {

    public CropNotFoundException(Long cropId) {
        super("Crop not found: " + cropId);
    }
}
