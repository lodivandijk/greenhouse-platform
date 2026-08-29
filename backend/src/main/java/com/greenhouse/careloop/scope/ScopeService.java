package com.greenhouse.careloop.scope;

import com.greenhouse.careloop.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Scope answers "is this record relevant to this loop", and nothing else.
// It is deliberately NOT lifecycle: a rejected decision, a resolved assessment
// and a failed execution all normally stay IN_SCOPE, because they are all part
// of the honest history of what happened (ADR-021 section 5.4).
@Service
public class ScopeService {

    private final LoopRecordScopeEventRepository scopeEventRepository;

    public ScopeService(LoopRecordScopeEventRepository scopeEventRepository) {
        this.scopeEventRepository = scopeEventRepository;
    }

    @Transactional
    public LoopRecordScopeEvent recordScope(
            Long careLoopId,
            LoopRecordType recordType,
            Long recordId,
            LoopScope scope,
            String reasonCode,
            String reasonText,
            ActorType actorType,
            String actorId,
            Instant occurredAt,
            String requestId
    ) {
        return scopeEventRepository.save(new LoopRecordScopeEvent(
                careLoopId, recordType, recordId, scope, reasonCode, reasonText,
                actorType, actorId, occurredAt, requestId
        ));
    }

    // Effective scope is simply the latest event for this loop-record pair.
    // Absent any event, a record linked to a loop is treated as in scope -
    // ordinary operation never requires a human to administer scope.
    public LoopScope effectiveScope(Long careLoopId, LoopRecordType recordType, Long recordId) {
        return scopeEventRepository
                .findFirstByCareLoopIdAndRecordTypeAndRecordIdOrderByOccurredAtDescIdDesc(
                        careLoopId, recordType, recordId)
                .map(LoopRecordScopeEvent::getScope)
                .orElse(LoopScope.IN_SCOPE);
    }

    public boolean isInScope(Long careLoopId, LoopRecordType recordType, Long recordId) {
        return effectiveScope(careLoopId, recordType, recordId) == LoopScope.IN_SCOPE;
    }

    public List<LoopRecordScopeEvent> scopeHistory(Long careLoopId) {
        return scopeEventRepository.findAllByCareLoopIdOrderByOccurredAtAsc(careLoopId);
    }

    public Optional<LoopRecordScopeEvent> latestScopeEvent(
            Long careLoopId, LoopRecordType recordType, Long recordId
    ) {
        return scopeEventRepository.findFirstByCareLoopIdAndRecordTypeAndRecordIdOrderByOccurredAtDescIdDesc(
                careLoopId, recordType, recordId);
    }
}
