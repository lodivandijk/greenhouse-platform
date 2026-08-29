package com.greenhouse.careloop.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DecisionLifecycleEventRepository extends JpaRepository<DecisionLifecycleEvent, Long> {

    List<DecisionLifecycleEvent> findAllByDecisionIdOrderByOccurredAtAsc(Long decisionId);

    Optional<DecisionLifecycleEvent> findFirstByDecisionIdOrderByOccurredAtDescIdDesc(Long decisionId);

    boolean existsByDecisionIdAndEventType(Long decisionId, DecisionLifecycleEventType eventType);
}
