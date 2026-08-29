package com.greenhouse.careloop.command;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommandRepository extends JpaRepository<Command, Long> {

    List<Command> findAllByCareLoopIdOrderByIssuedAtDesc(Long careLoopId);

    Optional<Command> findByDecisionId(Long decisionId);

    boolean existsByDecisionId(Long decisionId);
}
