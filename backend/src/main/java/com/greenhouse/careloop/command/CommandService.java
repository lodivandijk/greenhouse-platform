package com.greenhouse.careloop.command;

import com.greenhouse.careloop.ActorType;
import com.greenhouse.careloop.InvalidLoopTransitionException;
import com.greenhouse.careloop.command.catalogue.CommandCatalogue;
import com.greenhouse.careloop.command.catalogue.CommandDefinition;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.common.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandService.class);

    private final CommandRepository commandRepository;
    private final CommandLifecycleEventRepository lifecycleEventRepository;
    private final DecisionService decisionService;
    private final CommandCatalogue commandCatalogue;
    private final ScopeService scopeService;
    private final Clock clock;

    public CommandService(
            CommandRepository commandRepository,
            CommandLifecycleEventRepository lifecycleEventRepository,
            DecisionService decisionService,
            CommandCatalogue commandCatalogue,
            ScopeService scopeService,
            Clock clock
    ) {
        this.commandRepository = commandRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.decisionService = decisionService;
        this.commandCatalogue = commandCatalogue;
        this.scopeService = scopeService;
        this.clock = clock;
    }

    // Called only after a decision has been approved. The unique index on
    // command.decision_id backs this up at the database level, so a retried
    // approval cannot produce a second command.
    @Transactional
    public Command issueFromApprovedDecision(Long decisionId, String requestId) {
        Decision decision = decisionService.requireDecision(decisionId);

        if (!decisionService.isApproved(decisionId)) {
            throw new InvalidLoopTransitionException(
                    "Decision " + decisionId + " is not approved; a command may only be issued from an "
                            + "approved decision.");
        }

        Optional<Command> existing = commandRepository.findByDecisionId(decisionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CommandDefinition definition = commandCatalogue.definitionFor(decision.getActionType());
        Instant now = clock.instant();

        Command command = new Command();
        command.setCareLoopId(decision.getCareLoopId());
        command.setDecisionId(decisionId);
        command.setCommandType(decision.getActionType());
        // Human-targeted only in this version; the database CHECK constraint
        // enforces the same rule independently.
        command.setTargetType(CommandTargetType.HUMAN);
        command.setParameters(decision.getParameters());
        command.setIssuedAt(now);
        command.setExpiresAt(now.plus(definition.defaultExpiry()));

        Command saved = commandRepository.save(command);

        lifecycleEventRepository.save(new CommandLifecycleEvent(
                saved.getId(), CommandLifecycleEventType.ISSUED, null, null,
                ActorType.DETERMINISTIC_ENGINE, null, now, requestId
        ));

        scopeService.recordScope(
                decision.getCareLoopId(), LoopRecordType.COMMAND, saved.getId(), LoopScope.IN_SCOPE,
                "AUTOMATIC_COMMAND_ISSUE", "Command issued from an approved in-scope decision.",
                ActorType.DETERMINISTIC_ENGINE, null, now, requestId
        );

        LOGGER.info(
                "Command issued: id={} loop={} type={} fromDecision={}",
                saved.getId(), saved.getCareLoopId(), saved.getCommandType(), decisionId
        );
        return saved;
    }

    public Command requireCommand(Long commandId) {
        return commandRepository.findById(commandId)
                .orElseThrow(() -> new DomainValidationException("Unknown command: " + commandId));
    }

    public Optional<CommandLifecycleEventType> currentState(Long commandId) {
        return lifecycleEventRepository.findFirstByCommandIdOrderByOccurredAtDescIdDesc(commandId)
                .map(CommandLifecycleEvent::getEventType);
    }

    public List<CommandLifecycleEvent> history(Long commandId) {
        return lifecycleEventRepository.findAllByCommandIdOrderByOccurredAtAsc(commandId);
    }

    public List<Command> forLoop(Long careLoopId) {
        return commandRepository.findAllByCareLoopIdOrderByIssuedAtDesc(careLoopId);
    }

    @Transactional
    public CommandLifecycleEvent recordResponse(
            Long commandId,
            CommandLifecycleEventType response,
            String reasonText,
            Instant deferredUntil,
            ActorType actorType,
            String actorId,
            String requestId
    ) {
        if (response != CommandLifecycleEventType.ACKNOWLEDGED
                && response != CommandLifecycleEventType.DEFERRED
                && response != CommandLifecycleEventType.DECLINED) {
            throw new DomainValidationException(
                    "response must be ACKNOWLEDGED, DEFERRED or DECLINED. ISSUED, CANCELLED and EXPIRED are "
                            + "recorded by the system.");
        }

        requireCommand(commandId);

        CommandLifecycleEventType state = currentState(commandId)
                .orElseThrow(() -> new InvalidLoopTransitionException(
                        "Command " + commandId + " has no lifecycle history."));

        if (state == CommandLifecycleEventType.DECLINED
                || state == CommandLifecycleEventType.CANCELLED
                || state == CommandLifecycleEventType.EXPIRED) {
            throw new InvalidLoopTransitionException(
                    "Command " + commandId + " is " + state + " and can no longer be responded to.");
        }

        CommandLifecycleEvent event = lifecycleEventRepository.save(new CommandLifecycleEvent(
                commandId, response, reasonText, deferredUntil, actorType, actorId, clock.instant(), requestId
        ));

        LOGGER.info("Command {}: id={} actor={}", response, commandId, actorType);
        return event;
    }
}
