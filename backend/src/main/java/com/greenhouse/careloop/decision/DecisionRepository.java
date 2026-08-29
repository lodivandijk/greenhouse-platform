package com.greenhouse.careloop.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findAllByCareLoopIdOrderByProposedAtDesc(Long careLoopId);

    List<Decision> findAllBySupersedesDecisionId(Long supersedesDecisionId);
}
