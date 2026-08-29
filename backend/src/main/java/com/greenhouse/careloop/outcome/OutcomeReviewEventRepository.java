package com.greenhouse.careloop.outcome;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutcomeReviewEventRepository extends JpaRepository<OutcomeReviewEvent, Long> {

    List<OutcomeReviewEvent> findAllByOutcomeIdOrderByOccurredAtAsc(Long outcomeId);
}
