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
        for (AssessmentResponse resolved : changes.resolved()) {
            closeLoopIfRecovered(resolved, evaluatedAt);
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

    private void closeLoopIfRecovered(AssessmentResponse assessment, Instant evaluatedAt) {
        String loopKey = loopCorrelationKey(assessment);
        Optional<CareLoop> existing = careLoopRepository.findByCorrelationKeyAndClosedAtIsNull(loopKey);
        if (existing.isEmpty()) {
            return;
        }

        CareLoop loop = existing.get();

        Instant resolvedAt = assessment.resolvedAt() == null ? evaluatedAt : assessment.resolvedAt();
        Duration recovery = recoveryDurationFor(assessment);
        if (Duration.between(resolvedAt, evaluatedAt).compareTo(recovery) < 0) {
            return;
        }

        // Another still-active assessment linked to this loop keeps it open -
        // e.g. one crop recovered but others in the same ventilation loop have
        // not.
        boolean otherActiveLinked = careLoopAssessmentRepository.findAllByCareLoopId(loop.getId()).stream()
                .map(CareLoopAssessment::getAssessmentId)
                .filter(id -> !id.equals(assessment.id()))
                .map(assessmentRepository::findById)
                .flatMap(Optional::stream)
                .anyMatch(a -> a.getStatus() == AssessmentStatus.ACTIVE);
        if (otherActiveLinked) {
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

    private Duration recoveryDurationFor(AssessmentResponse assessment) {
        return profileFor(assessment)
                .map(CropMonitoringProfile::temperatureRecoveryDuration)
                .orElse(DEFAULT_RECOVERY);
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
