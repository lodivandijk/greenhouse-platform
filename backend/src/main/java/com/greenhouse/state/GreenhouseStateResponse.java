package com.greenhouse.state;

import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.twin.model.GreenhouseTwin;

import java.time.Instant;
import java.util.List;

public record GreenhouseStateResponse(
        Instant generatedAt,
        GreenhouseTwin twin,
        List<AssessmentResponse> assessments
) {
}
