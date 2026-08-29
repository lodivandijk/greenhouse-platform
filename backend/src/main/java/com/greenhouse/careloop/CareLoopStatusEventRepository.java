package com.greenhouse.careloop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareLoopStatusEventRepository extends JpaRepository<CareLoopStatusEvent, Long> {

    List<CareLoopStatusEvent> findAllByCareLoopIdOrderByOccurredAtAsc(Long careLoopId);
}
