package com.greenhouse.careloop;

import com.greenhouse.assessment.AssessmentChanges;
import com.greenhouse.assessment.AssessmentCode;
import com.greenhouse.assessment.AssessmentEntity;
import com.greenhouse.assessment.AssessmentRepository;
import com.greenhouse.assessment.AssessmentResponse;
import com.greenhouse.assessment.AssessmentStatus;
import com.greenhouse.careloop.scope.LoopRecordType;
import com.greenhouse.careloop.scope.LoopScope;
import com.greenhouse.careloop.scope.ScopeService;
import com.greenhouse.crop.CropMonitoringProfile;
import com.greenhouse.crop.CropMonitoringProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// The seam between "a condition is being observed" and "a condition has
// persisted long enough to be worth a human's attention".
//
// The assessment itself is raised on the very first cycle a condition appears,
// because that is when the evidence starts. A care loop only opens once the
// condition has held for the crop's configured excursion duration, and only
// closes once it has been clear for the recovery duration. That separation is
// what stops a one-minute temperature blip from generating a task, without
// throwing away the evidence that the blip happened (ADR-021).
@Service
public class CareLoopCorrelationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CareLoopCorrelationService.class);

    // Assessments whose condition is inherently instantaneous - a probe is
    // either assigned or not, calibrated or not - do not wait for a duration.
    private static final List<AssessmentCode> IMMEDIATELY_ACTIONABLE = List.of(
            AssessmentCode.CROP_SENSOR_NOT_ASSIGNED,
            AssessmentCode.CROP_SENSOR_CALIBRATION_REQUIRED,
            AssessmentCode.CROP_SENSOR_DATA_STALE,
            AssessmentCode.DEVICE_OFFLINE,
            AssessmentCode.OBSERVATION_STALE
    );

    private static final Duration DEFAULT_EXCURSION = Duration.ofMinutes(60);
    private static final Duration DEFAULT_RECOVERY = Duration.ofMinutes(30);

    private final CareLoopRepository careLoopRepository;
    private final CareLoopAssessmentRepository careLoopAssessmentRepository;
    private final CareLoopStatusEventRepository statusEventRepository;
    private final AssessmentRepository assessmentRepository;
    private final CropMonitoringProfileService profileService;
    private final ScopeService scopeService;

    public CareLoopCorrelationService(
            CareLoopRepository careLoopRepository,
            CareLoopAssessmentRepository careLoopAssessmentRepository,
            CareLoopStatusEventRepository statusEventRepository,
            AssessmentRepository assessmentRepository,
            CropMonitoringProfileService profileService,
            ScopeService scopeService
    ) {
        this.careLoopRepository = careLoopRepository;
        this.careLoopAssessmentRepository = careLoopAssessmentRepository;
        this.statusEventRepository = statusEventRepository;
        this.assessmentRepository = assessmentRepository;
        this.profileService = profileService;
        this.scopeService = scopeService;
    }

    @Transactional
    public void correlate(AssessmentChanges changes, Instant evaluatedAt) {
        for (AssessmentResponse raised : changes.raised()) {
            openLoopIfDue(raised, evaluatedAt);
        }
        for (AssessmentResponse updated : changes.updated()) {
            openLoopIfDue(updated, evaluatedAt);
        }

        // Recovery is driven by loop STATE, not by this tick's deltas.
        //
        // Closing used to be attempted only for assessments in
        // changes.resolved(), which holds only what resolved on this very tick
        // - at which point no time has passed since resolution, so the recovery
        // gate always declined, and no later tick ever revisited it (the
        // reconciler only resolves rows that are still ACTIVE). Loops stayed
        // open forever. Every tick now reconsiders every open loop instead.
        closeRecoveredLoops(evaluatedAt);
    }

    private void closeRecoveredLoops(Instant evaluatedAt) {
        for (CareLoop loop : careLoopRepository.findAllByClosedAtIsNullOrderByOpenedAtDesc()) {
            closeIfRecovered(loop, evaluatedAt);
        }
    }

    private void openLoopIfDue(AssessmentResponse assessment, Instant evaluatedAt) {
        String loopKey = loopCorrelationKey(assessment);

        Optional<CareLoop> existing = careLoopRepository.findByCorrelationKeyAndClosedAtIsNull(loopKey);
        if (existing.isPresent()) {
            linkAssessment(existing.get(), assessment, evaluatedAt);
            return;
        }

        if (!hasPersistedLongEnough(assessment, evaluatedAt)) {
            return;
        }

        CareLoop loop = new CareLoop();
        loop.setPrimarySubjectType(subjectTypeOf(assessment));
        loop.setPrimarySubjectId(subjectIdOf(assessment));
        loop.setConditionType(assessment.code().name());
        loop.setCorrelationKey(loopKey);
        loop.setOpenedAt(evaluatedAt);
        loop.setCreatedBy(ActorType.DETERMINISTIC_ENGINE);

        CareLoop saved = careLoopRepository.save(loop);

        statusEventRepository.save(new CareLoopStatusEvent(
                saved.getId(), CareLoopStatus.OPEN, "CONDITION_PERSISTED",
                "Condition persisted beyond its configured excursion duration.",
                ActorType.DETERMINISTIC_ENGINE, null, evaluatedAt, null
        ));

        linkAssessment(saved, assessment, evaluatedAt);

        LOGGER.info(
                "Care loop opened: id={} correlationKey={} condition={}",
                saved.getId(), saved.getCorrelationKey(), saved.getConditionType()
        );
    }

    private void linkAssessment(CareLoop loop, AssessmentResponse assessment, Instant evaluatedAt) {
        if (careLoopAssessmentRepository.existsByCareLoopIdAndAssessmentId(loop.getId(), assessment.id())) {
            return;
        }

        careLoopAssessmentRepository.save(
                new CareLoopAssessment(loop.getId(), assessment.id(), evaluatedAt));

        // Automatic scope: an assessment that opens or joins a loop is
        // relevant to it by definition. A human only intervenes to say
        // otherwise (ADR-021 section 5.3).
        scopeService.recordScope(
                loop.getId(), LoopRecordType.ASSESSMENT, assessment.id(), LoopScope.IN_SCOPE,
                "AUTOMATIC_ASSESSMENT_LINK", "Assessment opened or joined this loop.",
                ActorType.DETERMINISTIC_ENGINE, null, evaluatedAt, null
        );
    }

    // Closes one open loop if every assessment supporting it has resolved and
    // the most recent of those resolutions is older than the recovery duration.
    //
    // Reading the loop's own linked assessments rather than a tick delta is
    // what makes this work on any later tick, not just the one that happened to
    // resolve something.
    private void closeIfRecovered(CareLoop loop, Instant evaluatedAt) {
        List<AssessmentEntity> linked = careLoopAssessmentRepository.findAllByCareLoopId(loop.getId()).stream()
                .map(CareLoopAssessment::getAssessmentId)
                .map(assessmentRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        if (linked.isEmpty()) {
            return;
        }

        // Any still-active supporting assessment keeps the loop open - one crop
        // recovering does not close a shared greenhouse ventilation loop while
        // others are still too warm.
        boolean anyStillActive = linked.stream()
                .anyMatch(assessment -> assessment.getStatus() == AssessmentStatus.ACTIVE);
        if (anyStillActive) {
            return;
        }

        Optional<Instant> latestResolvedAt = linked.stream()
                .map(AssessmentEntity::getResolvedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo);
        if (latestResolvedAt.isEmpty()) {
            return;
        }

        Duration recovery = recoveryDurationForLoop(linked);
        if (Duration.between(latestResolvedAt.get(), evaluatedAt).compareTo(recovery) < 0) {
            // Resolved, but not yet clear for long enough. A later tick will
            // reconsider this same loop.
            return;
        }

        loop.setClosedAt(evaluatedAt);
        careLoopRepository.save(loop);

        statusEventRepository.save(new CareLoopStatusEvent(
                loop.getId(), CareLoopStatus.CLOSED, "CONDITION_RECOVERED",
                "Condition resolved and stayed clear for the configured recovery duration.",
                ActorType.DETERMINISTIC_ENGINE, null, evaluatedAt, null
        ));

        LOGGER.info("Care loop closed: id={} correlationKey={}", loop.getId(), loop.getCorrelationKey());
    }

    // The longest recovery duration among the loop's crops, so a shared loop
    // waits for the most cautious crop rather than the first one to recover.
    private Duration recoveryDurationForLoop(List<AssessmentEntity> linked) {
        return linked.stream()
                .map(AssessmentEntity::getCropId)
                .filter(Objects::nonNull)
                .distinct()
                .map(profileService::findEnabledProfile)
                .flatMap(Optional::stream)
                .map(CropMonitoringProfile::temperatureRecoveryDuration)
                .max(Duration::compareTo)
                .orElse(DEFAULT_RECOVERY);
    }

    private boolean hasPersistedLongEnough(AssessmentResponse assessment, Instant evaluatedAt) {
        if (IMMEDIATELY_ACTIONABLE.contains(assessment.code())) {
            return true;
        }

        Duration required = excursionDurationFor(assessment);
        Instant firstDetectedAt = assessment.firstDetectedAt();
        if (firstDetectedAt == null) {
            return false;
        }
        return Duration.between(firstDetectedAt, evaluatedAt).compareTo(required) >= 0;
    }

    private Duration excursionDurationFor(AssessmentResponse assessment) {
        return profileFor(assessment)
                .map(CropMonitoringProfile::temperatureExcursionDuration)
                .orElse(DEFAULT_EXCURSION);
    }

    private Optional<CropMonitoringProfile> profileFor(AssessmentResponse assessment) {
        return assessmentRepository.findById(assessment.id())
                .map(AssessmentEntity::getCropId)
                .flatMap(profileService::findEnabledProfile);
    }

    // Crop temperature assessments deliberately collapse into ONE greenhouse
    // loop: the response (ventilate, shade) is a single physical act, so six
    // crops being too warm should produce one task, not six.
    private String loopCorrelationKey(AssessmentResponse assessment) {
        if (assessment.code() == AssessmentCode.CROP_TEMPERATURE_ABOVE_PREFERRED) {
            return "GREENHOUSE:" + assessment.greenhouseId() + ":TEMPERATURE_HIGH";
        }
        if (assessment.code() == AssessmentCode.CROP_TEMPERATURE_BELOW_PREFERRED) {
            return "GREENHOUSE:" + assessment.greenhouseId() + ":TEMPERATURE_LOW";
        }
        return assessment.scopeType().name() + ":" + assessment.scopeId() + ":" + assessment.code().name();
    }

    private CareLoopSubjectType subjectTypeOf(AssessmentResponse assessment) {
        return switch (assessment.code()) {
            case CROP_TEMPERATURE_ABOVE_PREFERRED, CROP_TEMPERATURE_BELOW_PREFERRED -> CareLoopSubjectType.GREENHOUSE;
            default -> switch (assessment.scopeType()) {
                case CROP -> CareLoopSubjectType.CROP;
                case DEVICE -> CareLoopSubjectType.DEVICE;
                default -> CareLoopSubjectType.GREENHOUSE;
            };
        };
    }

    private String subjectIdOf(AssessmentResponse assessment) {
        return switch (assessment.code()) {
            case CROP_TEMPERATURE_ABOVE_PREFERRED, CROP_TEMPERATURE_BELOW_PREFERRED -> assessment.greenhouseId();
            default -> assessment.scopeId();
        };
    }
}
