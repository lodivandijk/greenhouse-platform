package com.greenhouse.assessment;

import java.util.List;

public record AssessmentChanges(
        List<AssessmentResponse> raised,
        List<AssessmentResponse> updated,
        List<AssessmentResponse> resolved
) {

    public boolean hasChanges() {
        return !raised.isEmpty() || !updated.isEmpty() || !resolved.isEmpty();
    }
}
