package com.greenhouse.evaluation;

import com.greenhouse.assessment.AssessmentChanges;

import java.time.Instant;

public record EvaluationResult(
        Instant evaluatedAt,
        String greenhouseId,
        AssessmentChanges assessmentChanges
) {
}
