package com.greenhouse.assessment;

import com.greenhouse.assessment.reconciliation.AssessmentReconciler;
import com.greenhouse.assessment.rule.AssessmentRule;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssessmentService.class);

    private final List<AssessmentRule> rules;
    private final AssessmentReconciler assessmentReconciler;

    public AssessmentService(List<AssessmentRule> rules, AssessmentReconciler assessmentReconciler) {
        this.rules = rules;
        this.assessmentReconciler = assessmentReconciler;
    }

    public AssessmentChanges assessAndReconcile(GreenhouseTwin twin, Instant evaluatedAt) {
        List<AssessmentFinding> findings = evaluate(twin, evaluatedAt);
        validateNoDuplicateCorrelationKeys(findings);
        return assessmentReconciler.reconcile(twin.greenhouseId(), findings, evaluatedAt);
    }

    public List<AssessmentFinding> evaluate(GreenhouseTwin twin, Instant evaluatedAt) {
        return rules.stream()
                .flatMap(rule -> rule.evaluate(twin, evaluatedAt).stream())
                .toList();
    }

    private void validateNoDuplicateCorrelationKeys(List<AssessmentFinding> findings) {
        Map<String, List<String>> ruleIdsByCorrelationKey = findings.stream()
                .collect(Collectors.groupingBy(
                        AssessmentFinding::correlationKey,
                        Collectors.mapping(AssessmentFinding::ruleId, Collectors.toList())
                ));

        ruleIdsByCorrelationKey.forEach((correlationKey, ruleIds) -> {
            if (ruleIds.size() > 1) {
                LOGGER.error(
                        "Duplicate correlation key '{}' produced by rules: {}",
                        correlationKey, ruleIds
                );
                throw new IllegalStateException(
                        "Duplicate correlation key '" + correlationKey + "' produced by rules: " + ruleIds
                );
            }
        });
    }
}
