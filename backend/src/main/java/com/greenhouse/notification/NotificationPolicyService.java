package com.greenhouse.notification;

import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentSeverity;
import com.greenhouse.briefing.DailyBriefingSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import com.greenhouse.briefing.DailyBriefingSnapshotRepository;
import com.greenhouse.careloop.CareLoop;
import com.greenhouse.careloop.CareLoopAssessment;
import com.greenhouse.careloop.CareLoopAssessmentRepository;
import com.greenhouse.careloop.CareLoopProjectionService;
import com.greenhouse.careloop.CareLoopRepository;
import com.greenhouse.careloop.CareLoopStatus;
import com.greenhouse.careloop.CareLoopStatusEvent;
import com.greenhouse.careloop.CareLoopStatusEventRepository;
import com.greenhouse.careloop.command.Command;
import com.greenhouse.careloop.command.CommandLifecycleEvent;
import com.greenhouse.careloop.command.CommandLifecycleEventType;
import com.greenhouse.careloop.command.CommandService;
import com.greenhouse.careloop.decision.Decision;
import com.greenhouse.careloop.decision.DecisionLifecycleEvent;
import com.greenhouse.careloop.decision.DecisionService;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.ScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Decides whether a notification should exist. It reads care-loop and briefing
// state and writes only notification_intent - it never mutates the records it
// reports on (ADR-023).
@Service
public class NotificationPolicyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationPolicyService.class);

    private static final NotificationAudience AUDIENCE = NotificationAudience.PRIMARY_CARETAKER;

    // Statuses where a human genuinely has something to do. OPEN and
    // EVALUATING_OUTCOME are excluded on purpose: the platform is working and
    // nothing is being waited on from a person.
    private static final List<CareLoopStatus> ACTIONABLE = List.of(
            CareLoopStatus.AWAITING_HUMAN_REVIEW,
            CareLoopStatus.AWAITING_DECISION_APPROVAL,
            CareLoopStatus.AWAITING_COMMAND_ACKNOWLEDGEMENT,
            CareLoopStatus.AWAITING_EXECUTION,
            CareLoopStatus.BLOCKED
    );

    private final NotificationIntentRepository intentRepository;
    private final NotificationIntentWriter intentWriter;
    private final NotificationDeliveryEventRepository deliveryEventRepository;
    private final NotificationProperties properties;
    private final CareLoopRepository careLoopRepository;
    private final CareLoopAssessmentRepository careLoopAssessmentRepository;
    private final CareLoopStatusEventRepository statusEventRepository;
    private final CareLoopProjectionService projectionService;
    private final DecisionService decisionService;
    private final CommandService commandService;
    private final ScopeService scopeService;
    private final AssessmentRepository assessmentRepository;
    private final DailyBriefingSnapshotRepository briefingSnapshotRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public NotificationPolicyService(
            NotificationIntentRepository intentRepository,
            NotificationIntentWriter intentWriter,
            NotificationDeliveryEventRepository deliveryEventRepository,
            NotificationProperties properties,
            CareLoopRepository careLoopRepository,
            CareLoopAssessmentRepository careLoopAssessmentRepository,
            CareLoopStatusEventRepository statusEventRepository,
            CareLoopProjectionService projectionService,
            DecisionService decisionService,
            CommandService commandService,
            ScopeService scopeService,
            AssessmentRepository assessmentRepository,
            DailyBriefingSnapshotRepository briefingSnapshotRepository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.intentWriter = intentWriter;
        this.deliveryEventRepository = deliveryEventRepository;
        this.properties = properties;
        this.careLoopRepository = careLoopRepository;
        this.careLoopAssessmentRepository = careLoopAssessmentRepository;
        this.statusEventRepository = statusEventRepository;
        this.projectionService = projectionService;
        this.decisionService = decisionService;
        this.commandService = commandService;
        this.scopeService = scopeService;
        this.assessmentRepository = assessmentRepository;
        this.briefingSnapshotRepository = briefingSnapshotRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public List<NotificationIntent> findAndRecordCandidates() {
        List<NotificationIntent> created = new ArrayList<>();
        created.addAll(briefingCandidates());
        created.addAll(careLoopCandidates());
        for (NotificationIntent intent : created) {
            meterRegistry.counter(
                    "greenhouse.notifications.intents.created",
                    "type", intent.getIntentType().name(),
                    "priority", intent.getPriority().name()
            ).increment();
        }
        return created;
    }

    // --- daily briefing -------------------------------------------------

    private List<NotificationIntent> briefingCandidates() {
        List<NotificationIntent> created = new ArrayList<>();
        Instant cutoff = clock.instant().minus(properties.briefingRelevanceWindow());

        for (DailyBriefingSnapshot snapshot : briefingSnapshotRepository.findAll()) {
            if (snapshot.getGeneratedAt().isBefore(cutoff)) {
                // Older than its relevance window - it was either already sent
                // or is now history, and either way must not be emailed today.
                continue;
            }

            String key = "DAILY_BRIEFING:" + snapshot.getId() + ":" + AUDIENCE;
            if (intentRepository.existsByDeduplicationKey(key)) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("greenhouseDay", String.valueOf(snapshot.getGreenhouseDay()));
            payload.put("generatedAt", String.valueOf(snapshot.getGeneratedAt()));
            payload.put("isUpdate", snapshot.getSupersedesSnapshotId() != null);
            payload.put("supersedesSnapshotId", snapshot.getSupersedesSnapshotId());
            payload.put("briefing", snapshot.getSnapshot());

            NotificationIntent intent = new NotificationIntent();
            intent.setIntentType(NotificationIntentType.DAILY_BRIEFING);
            intent.setPriority(NotificationPriority.NORMAL);
            intent.setAudience(AUDIENCE);
            intent.setBriefingSnapshotId(snapshot.getId());
            intent.setSourceType("DAILY_BRIEFING_SNAPSHOT");
            intent.setSourceId(String.valueOf(snapshot.getId()));
            intent.setDeduplicationKey(key);
            intent.setPayload(payload);
            intent.setNotBefore(snapshot.getGeneratedAt());
            intent.setExpiresAt(snapshot.getGeneratedAt().plus(properties.briefingRelevanceWindow()));
            intent.setCreatedAt(clock.instant());

            persist(intent).ifPresent(created::add);
        }

        return created;
    }

    // --- care loops -----------------------------------------------------

    private List<NotificationIntent> careLoopCandidates() {
        List<NotificationIntent> created = new ArrayList<>();

        for (CareLoop loop : careLoopRepository.findAllByClosedAtIsNullOrderByOpenedAtDesc()) {
            CareLoopStatus status = projectionService.projectStatus(loop.getId());
            if (!ACTIONABLE.contains(status)) {
                continue;
            }

            ActionableState state = actionableState(loop, status);
            actionRequiredCandidate(loop, state).ifPresent(created::add);
            reminderCandidate(loop, state).ifPresent(created::add);
        }

        return created;
    }

    private Optional<NotificationIntent> actionRequiredCandidate(CareLoop loop, ActionableState state) {
        String key = "CARE_LOOP:" + loop.getId() + ":ACTION:" + state.fingerprint() + ":" + AUDIENCE;
        if (intentRepository.existsByDeduplicationKey(key)) {
            return Optional.empty();
        }

        NotificationIntent intent = careLoopIntent(
                loop, state, NotificationIntentType.ACTION_REQUIRED, key, clock.instant());
        return persist(intent);
    }

    // A reminder only exists once the same actionable state has survived the
    // reminder interval since a successful action-required delivery. The bucket
    // in the key is what stops it repeating every sweep, without putting the
    // raw clock in the key.
    private Optional<NotificationIntent> reminderCandidate(CareLoop loop, ActionableState state) {
        String actionKey = "CARE_LOOP:" + loop.getId() + ":ACTION:" + state.fingerprint() + ":" + AUDIENCE;

        Optional<NotificationIntent> actionIntent = intentRepository.findByDeduplicationKey(actionKey);
        if (actionIntent.isEmpty()) {
            return Optional.empty();
        }

        Optional<Instant> sentAt = firstSentAt(actionIntent.get().getId());
        if (sentAt.isEmpty()) {
            // Never actually delivered, so there is nothing to remind about yet.
            return Optional.empty();
        }

        Instant now = clock.instant();
        long intervalSeconds = properties.reminderInterval().getSeconds();
        long elapsed = now.getEpochSecond() - sentAt.get().getEpochSecond();
        if (elapsed < intervalSeconds) {
            return Optional.empty();
        }

        long bucket = elapsed / intervalSeconds;
        String key = "CARE_LOOP:" + loop.getId() + ":REMINDER:" + state.fingerprint()
                + ":" + bucket + ":" + AUDIENCE;
        if (intentRepository.existsByDeduplicationKey(key)) {
            return Optional.empty();
        }

        return persist(careLoopIntent(loop, state, NotificationIntentType.REMINDER, key, now));
    }

    private NotificationIntent careLoopIntent(
            CareLoop loop, ActionableState state, NotificationIntentType type, String key, Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("careLoopId", loop.getId());
        payload.put("subjectType", String.valueOf(loop.getPrimarySubjectType()));
        payload.put("subjectId", loop.getPrimarySubjectId());
        payload.put("conditionType", loop.getConditionType());
        payload.put("status", state.status().name());
        payload.put("nextRequiredAction", projectionService.nextRequiredAction(loop.getId()));
        payload.put("openedAt", String.valueOf(loop.getOpenedAt()));
        payload.put("pendingDecisionId", state.decisionId());
        payload.put("pendingCommandId", state.commandId());
        payload.put("assessments", state.assessmentEvidence());
        payload.put("firstDetectedAt", state.firstDetectedAt() == null
                ? null : String.valueOf(state.firstDetectedAt()));
        payload.put("actionableFingerprint", state.fingerprint());

        NotificationIntent intent = new NotificationIntent();
        intent.setIntentType(type);
        intent.setPriority(state.priority());
        intent.setAudience(AUDIENCE);
        intent.setCareLoopId(loop.getId());
        intent.setSourceType("CARE_LOOP_ACTIONABLE_STATE");
        intent.setSourceId(state.fingerprint());
        intent.setDeduplicationKey(key);
        intent.setPayload(payload);
        intent.setNotBefore(now);
        intent.setCreatedAt(now);
        return intent;
    }

    // --- actionable state + fingerprint ---------------------------------

    // Everything that determines whether the human's required action has
    // genuinely changed. Notably NOT the clock: repeated sweeps of unchanged
    // state must produce an identical fingerprint.
    public ActionableState actionableState(CareLoop loop, CareLoopStatus status) {
        Optional<Decision> effective = projectionService.effectiveDecision(loop.getId());
        Long decisionId = effective.map(Decision::getId).orElse(null);
        String decisionEvent = effective
                .flatMap(decision -> decisionService.history(decision.getId()).stream()
                        .reduce((first, second) -> second))
                .map(event -> event.getId() + ":" + event.getEventType())
                .orElse("-");

        Optional<Command> pendingCommand = commandService.forLoop(loop.getId()).stream()
                .filter(command -> {
                    CommandLifecycleEventType commandState = commandService.currentState(command.getId())
                            .orElse(CommandLifecycleEventType.ISSUED);
                    return commandState != CommandLifecycleEventType.CANCELLED
                            && commandState != CommandLifecycleEventType.EXPIRED;
                })
                .findFirst();
        Long commandId = pendingCommand.map(Command::getId).orElse(null);
        String commandEvent = pendingCommand
                .flatMap(command -> commandService.history(command.getId()).stream()
                        .reduce((first, second) -> second))
                .map(event -> event.getId() + ":" + event.getEventType())
                .orElse("-");

        String statusEvent = statusEventRepository
                .findAllByCareLoopIdOrderByOccurredAtAsc(loop.getId()).stream()
                .reduce((first, second) -> second)
                .map(event -> event.getId() + ":" + event.getStatus())
                .orElse("-");

        // Only in-scope assessments count. A human who excluded a record as
        // invalid must not have it drive alerts or priority.
        List<AssessmentEntity> inScope = careLoopAssessmentRepository.findAllByCareLoopId(loop.getId()).stream()
                .map(CareLoopAssessment::getAssessmentId)
                .filter(id -> scopeService.isInScope(loop.getId(), LoopRecordType.ASSESSMENT, id))
                .map(assessmentRepository::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(AssessmentEntity::getId))
                .toList();

        AssessmentSeverity highest = inScope.stream()
                .map(AssessmentEntity::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);

        Instant firstDetectedAt = inScope.stream()
                .map(AssessmentEntity::getFirstDetectedAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        List<Map<String, Object>> evidence = inScope.stream().map(assessment -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("assessmentId", assessment.getId());
            entry.put("code", String.valueOf(assessment.getCode()));
            entry.put("severity", String.valueOf(assessment.getSeverity()));
            entry.put("message", assessment.getMessage());
            entry.put("cropId", assessment.getCropId());
            entry.put("evidence", assessment.getEvidence());
            entry.put("monitoringProfileVersion", assessment.getMonitoringProfileVersion());
            entry.put("calibrationVersion", assessment.getCalibrationVersion());
            entry.put("firstDetectedAt", String.valueOf(assessment.getFirstDetectedAt()));
            return entry;
        }).toList();

        String raw = String.join("|",
                String.valueOf(loop.getId()),
                status.name(),
                String.valueOf(decisionId),
                decisionEvent,
                String.valueOf(commandId),
                commandEvent,
                statusEvent,
                inScope.stream().map(a -> String.valueOf(a.getId())).reduce("", (a, b) -> a + "," + b)
        );

        return new ActionableState(
                status, sha256(raw), NotificationPriority.fromSeverity(highest),
                decisionId, commandId, evidence, firstDetectedAt
        );
    }

    // Recomputed immediately before delivery. If it no longer matches, the
    // human's required action has moved on and the message is stale.
    public boolean isStillCurrent(NotificationIntent intent) {
        if (intent.getCareLoopId() == null) {
            // Briefings are historical snapshots and never go stale.
            return true;
        }

        Optional<CareLoop> loop = careLoopRepository.findById(intent.getCareLoopId());
        if (loop.isEmpty() || loop.get().getClosedAt() != null) {
            return false;
        }

        CareLoopStatus status = projectionService.projectStatus(loop.get().getId());
        if (!ACTIONABLE.contains(status)) {
            return false;
        }

        return actionableState(loop.get(), status).fingerprint().equals(intent.getSourceId());
    }

    private Optional<Instant> firstSentAt(Long intentId) {
        return deliveryEventRepository.findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(intentId).stream()
                .filter(event -> event.getEventType() == NotificationDeliveryEventType.SENT)
                .map(NotificationDeliveryEvent::getOccurredAt)
                .findFirst();
    }

    // Delegated to a separate bean so REQUIRES_NEW genuinely applies - see
    // NotificationIntentWriter for why self-invocation would not.
    private Optional<NotificationIntent> persist(NotificationIntent intent) {
        Optional<NotificationIntent> saved = intentWriter.saveIfAbsent(intent);
        if (saved.isEmpty()) {
            LOGGER.debug("Notification intent already exists: {}", intent.getDeduplicationKey());
        }
        return saved;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    public record ActionableState(
            CareLoopStatus status,
            String fingerprint,
            NotificationPriority priority,
            Long decisionId,
            Long commandId,
            List<Map<String, Object>> assessmentEvidence,
            Instant firstDetectedAt
    ) {
    }
}
