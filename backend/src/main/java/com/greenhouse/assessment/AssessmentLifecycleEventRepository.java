package com.greenhouse.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AssessmentLifecycleEventRepository extends JpaRepository<AssessmentLifecycleEvent, Long> {

    List<AssessmentLifecycleEvent> findAllByAssessmentIdOrderByOccurredAtAsc(Long assessmentId);

    List<AssessmentLifecycleEvent> findAllByOccurredAtAfterOrderByOccurredAtDesc(Instant since);

    List<AssessmentLifecycleEvent> findAllByCorrelationKeyOrderByOccurredAtAsc(String correlationKey);
}
