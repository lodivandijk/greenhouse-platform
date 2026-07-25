package com.greenhouse.twin.model;

import com.greenhouse.twin.status.AssessmentLevel;
import com.greenhouse.twin.status.EnvironmentCondition;

import java.util.Set;

public record EnvironmentAssessment(
        AssessmentLevel level,
        Set<EnvironmentCondition> conditions
) {
}
