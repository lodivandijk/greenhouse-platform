package com.greenhouse.assessment;

import java.time.Instant;
import java.util.List;

public record AssessmentListResponse(
        Instant generatedAt,
        List<AssessmentResponse> assessments
) {
}
