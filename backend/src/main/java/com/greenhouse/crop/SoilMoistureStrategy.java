package com.greenhouse.crop;

// How a crop's soil should be managed. Determines which side of the moisture
// index range is a problem: an EVENLY_MOIST crop (basil, mint) is assessed for
// drying out, a DRY_BETWEEN_WATERING crop (thyme, sage, oregano, tarragon) is
// assessed for staying wet too long.
public enum SoilMoistureStrategy {
    EVENLY_MOIST,
    DRY_BETWEEN_WATERING
}
