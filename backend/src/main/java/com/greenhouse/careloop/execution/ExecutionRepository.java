package com.greenhouse.careloop.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    List<Execution> findAllByCareLoopIdOrderByCompletedAtDesc(Long careLoopId);

    List<Execution> findAllByCommandIdOrderByCompletedAtDesc(Long commandId);

    boolean existsByCommandIdAndCorrectsExecutionIdIsNull(Long commandId);
}
