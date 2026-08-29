package com.greenhouse.careloop.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionGoalRepository extends JpaRepository<DecisionGoal, Long> {

    List<DecisionGoal> findAllByDecisionId(Long decisionId);
}
