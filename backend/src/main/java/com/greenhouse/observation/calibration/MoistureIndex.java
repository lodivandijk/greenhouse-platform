package com.greenhouse.observation.calibration;

// The result of converting a raw ADC reading through a sensor's calibration.
//
// Deliberately called a moisture INDEX, never a percentage or volumetric water
// content: it is a 0-100 position between this probe's own measured dry and wet
// reference points, not a physical measurement of water in soil. Two probes
// reading index 40 are not necessarily equally wet in absolute terms.
public record MoistureIndex(
        double value,
        int rawAdc,
        Long calibrationId,
        Integer calibrationVersion
) {
}
