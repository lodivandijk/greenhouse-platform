package com.greenhouse.crop;

// Whether this crop's soil is expected to be measured by a probe or judged by a
// human looking at it.
//
// The distinction exists because "no sensor assigned" and "no sensor wanted"
// are different facts that used to look identical to the deterministic engine,
// which meant a deliberately probe-less crop raised a data-quality assessment
// that could never be resolved (ADR-024).
//
// MANUAL suppresses sensor-derived soil assessment. It does NOT mean the soil
// is fine - it means the platform does not know and is not trying to. Anything
// presenting a MANUAL crop must say so rather than implying all is well.
public enum SoilMonitoringMode {
    SENSOR,
    MANUAL
}
