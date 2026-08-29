package com.greenhouse.careloop;

// Projected from the loop's immutable event history - never stored as a
// mutable column on care_loop. See CareLoopProjectionService.
public enum CareLoopStatus {
    OPEN,
    AWAITING_HUMAN_REVIEW,
    AWAITING_DECISION_APPROVAL,
    AWAITING_COMMAND_ACKNOWLEDGEMENT,
    AWAITING_EXECUTION,
    EVALUATING_OUTCOME,
    CLOSED,
    BLOCKED
}
