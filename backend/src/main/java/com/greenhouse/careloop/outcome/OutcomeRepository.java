package com.greenhouse.careloop.outcome;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutcomeRepository extends JpaRepository<Outcome, Long> {

    List<Outcome> findAllByCareLoopIdOrderByEvaluatedAtDesc(Long careLoopId);

    List<Outcome> findAllByExecutionIdOrderByEvaluatedAtDesc(Long executionId);

    List<Outcome> findAllByEvaluatedAtAfterOrderByEvaluatedAtDesc(Instant since);
}
