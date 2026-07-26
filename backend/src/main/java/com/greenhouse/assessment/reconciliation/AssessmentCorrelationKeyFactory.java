package com.greenhouse.assessment.reconciliation;

import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentScopeType;
import org.springframework.stereotype.Component;

@Component
public class AssessmentCorrelationKeyFactory {

    public String create(String greenhouseId, AssessmentScopeType scopeType, String scopeId, AssessmentCode code) {
        return greenhouseId + ":" + scopeType + ":" + scopeId + ":" + code;
    }
}
