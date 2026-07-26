package com.greenhouse.assessment;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AssessmentQueryService {

    // AssessmentSeverity is persisted as EnumType.STRING, so an ORDER BY on the raw
    // database column would sort alphabetically (ADVISORY, CRITICAL, WARNING) rather
    // than by actual severity - "descending by severity" is computed here instead.
    private static final Comparator<AssessmentResponse> DEFAULT_ORDER =
            Comparator.comparingInt((AssessmentResponse response) -> severityRank(response.severity()))
                    .reversed()
                    .thenComparing(AssessmentResponse::firstDetectedAt);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentMapper assessmentMapper;

    public AssessmentQueryService(AssessmentRepository assessmentRepository, AssessmentMapper assessmentMapper) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentMapper = assessmentMapper;
    }

    public List<AssessmentResponse> getActiveAssessments() {
        return getAssessmentsByStatus(AssessmentStatus.ACTIVE);
    }

    public List<AssessmentResponse> getAssessmentsByStatus(AssessmentStatus status) {
        return assessmentRepository.findAllByStatus(status).stream()
                .map(assessmentMapper::toResponse)
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    private static int severityRank(AssessmentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case ADVISORY -> 1;
        };
    }
}
