package com.greenhouse.careloop.scope;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoopRecordScopeEventRepository extends JpaRepository<LoopRecordScopeEvent, Long> {

    List<LoopRecordScopeEvent> findAllByCareLoopIdOrderByOccurredAtAsc(Long careLoopId);

    Optional<LoopRecordScopeEvent> findFirstByCareLoopIdAndRecordTypeAndRecordIdOrderByOccurredAtDescIdDesc(
            Long careLoopId, LoopRecordType recordType, Long recordId);
}
