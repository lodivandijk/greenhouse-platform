package com.greenhouse.careloop;

import java.time.Instant;

// A compact per-loop entry for listings: enough to decide what needs attention
// without pulling the full history.
public record OpenCareLoopSummary(
        Long id,
        CareLoopSubjectType primarySubjectType,
        String primarySubjectId,
        String conditionType,
        CareLoopStatus status,
        String nextRequiredAction,
        Instant openedAt,
        Long pendingDecisionId,
        Long pendingCommandId
) {
}
