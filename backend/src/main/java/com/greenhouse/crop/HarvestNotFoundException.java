package com.greenhouse.crop;

public class HarvestNotFoundException extends RuntimeException {

    public HarvestNotFoundException(Long harvestId) {
        super("Harvest not found: " + harvestId);
    }
}
