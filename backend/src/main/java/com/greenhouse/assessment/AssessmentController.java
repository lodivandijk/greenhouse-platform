package com.greenhouse.assessment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentQueryService assessmentQueryService;
    private final Clock clock;

    public AssessmentController(AssessmentQueryService assessmentQueryService, Clock clock) {
        this.assessmentQueryService = assessmentQueryService;
        this.clock = clock;
    }

    @GetMapping
    public AssessmentListResponse getAssessments(
            @RequestParam(name = "status", required = false) AssessmentStatus status
    ) {
        List<AssessmentResponse> assessments = status == null
                ? assessmentQueryService.getActiveAssessments()
                : assessmentQueryService.getAssessmentsByStatus(status);

        return new AssessmentListResponse(clock.instant(), assessments);
    }
}
