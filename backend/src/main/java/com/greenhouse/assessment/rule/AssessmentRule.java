package com.greenhouse.assessment.rule;

import com.greenhouse.assessment.AssessmentFinding;
import com.greenhouse.twin.model.GreenhouseTwin;

import java.time.Instant;
import java.util.List;

public interface AssessmentRule {

    List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt);

    String ruleId();

    int ruleVersion();
}
