package com.greenhouse.careloop.outcome;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutcomeEvaluationScheduleRepository extends JpaRepository<OutcomeEvaluationSchedule, Long> {

    List<OutcomeEvaluationSchedule> findAllByCompletedAtIsNullAndEvaluateAfterBefore(Instant now);

    Optional<OutcomeEvaluationSchedule> findByExecutionId(Long executionId);

    List<OutcomeEvaluationSchedule> findAllByCompletedAtIsNull();
}
