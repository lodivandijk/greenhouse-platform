package com.greenhouse.careloop.outcome;

// How a decision's success criteria should be judged once the evaluation
// window elapses. SENSOR_BASED requires a calibrated, fresh probe; if that
// evidence is unavailable the outcome is INCONCLUSIVE rather than assumed.
public enum OutcomeEvaluationMethod {
    SENSOR_BASED,
    HUMAN_CONFIRMED,
    HYBRID,
    ASSESSMENT_RESOLVED
}
