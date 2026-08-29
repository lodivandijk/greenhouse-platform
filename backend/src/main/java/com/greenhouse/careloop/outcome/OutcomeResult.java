package com.greenhouse.careloop.outcome;

// INCONCLUSIVE is a legitimate, expected result - it means the evidence needed
// to judge was not available (a stale probe, no follow-up observation). The
// system reports that honestly rather than defaulting to SUCCESS (ADR-021).
public enum OutcomeResult {
    SUCCESS,
    PARTIAL,
    FAILED,
    INCONCLUSIVE
}
