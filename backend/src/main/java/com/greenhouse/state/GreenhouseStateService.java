package com.greenhouse.state;

import com.greenhouse.assessment.AssessmentQueryService;
import com.greenhouse.twin.TwinService;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class GreenhouseStateService {

    private final TwinService twinService;
    private final AssessmentQueryService assessmentQueryService;
    private final Clock clock;

    public GreenhouseStateService(TwinService twinService, AssessmentQueryService assessmentQueryService, Clock clock) {
        this.twinService = twinService;
        this.assessmentQueryService = assessmentQueryService;
        this.clock = clock;
    }

    public GreenhouseStateResponse getCurrentState() {
        return new GreenhouseStateResponse(
                clock.instant(),
                twinService.getCurrentTwin(),
                assessmentQueryService.getActiveAssessments()
        );
    }
}
