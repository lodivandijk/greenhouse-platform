package com.greenhouse.careloop;

import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.DecisionLifecycleEvent;
import com.greenhouse.careloop.decision.DecisionLifecycleEventType;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

// Orchestrates the cross-record operations that must not half-happen.
//
// The important one is approval: recording the approval event and issuing the
// resulting command are a single transaction, so there is never an approved
// decision without its command, or a command without a recorded approval.
@Service
public class CareLoopService {

    private final DecisionService decisionService;
    private final CommandService commandService;
    private final ScopeService scopeService;
    private final CareLoopRepository careLoopRepository;
    private final Clock clock;

    public CareLoopService(
            DecisionService decisionService,
            CommandService commandService,
            ScopeService scopeService,
            CareLoopRepository careLoopRepository,
            Clock clock
    ) {
        this.decisionService = decisionService;
        this.commandService = commandService;
        this.scopeService = scopeService;
        this.careLoopRepository = careLoopRepository;
        this.clock = clock;
    }

    @Transactional
    public DecisionResponse respondToDecision(
            Long decisionId,
            DecisionLifecycleEventType response,
            String reasonText,
            ActorType actorType,
            String actorId,
            String requestId
    ) {
        DecisionLifecycleEvent event = decisionService.recordResponse(
                decisionId, response, reasonText, actorType, actorId, requestId);

        Command command = null;
        if (response == DecisionLifecycleEventType.APPROVED) {
            command = commandService.issueFromApprovedDecision(decisionId, requestId);
        }

        return new DecisionResponse(event, Optional.ofNullable(command));
    }

    // A human excluding a record from a loop - the exceptional case. The
    // record itself is untouched and remains readable; only its relevance to
    // this loop changes, and only with a stated reason (ADR-021).
    @Transactional
    public void recordScopeOverride(
            Long careLoopId,
            LoopRecordType recordType,
            Long recordId,
            LoopScope scope,
            String reasonCode,
            String reasonText,
            ActorType actorType,
            String actorId,
            String requestId
    ) {
        careLoopRepository.findById(careLoopId)
                .orElseThrow(() -> new CareLoopNotFoundException(careLoopId));

        if (reasonCode == null || reasonCode.isBlank()) {
            throw new DomainValidationException(
                    "reasonCode is required - a scope override must record why the record is or is not relevant.");
        }

        scopeService.recordScope(
                careLoopId, recordType, recordId, scope, reasonCode, reasonText,
                actorType == null ? ActorType.HUMAN_VIA_AGENT : actorType, actorId, clock.instant(), requestId
        );
    }

    public record DecisionResponse(DecisionLifecycleEvent event, Optional<Command> issuedCommand) {
    }
}
