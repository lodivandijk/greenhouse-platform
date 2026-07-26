package com.greenhouse.assessment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssessmentRepositoryTest {

    @Autowired
    private AssessmentRepository assessmentRepository;

    private static String uniqueCorrelationKey() {
        return "test-greenhouse:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT:" + UUID.randomUUID();
    }

    private static AssessmentEntity newActiveEntity(String correlationKey, Instant now) {
        return new AssessmentEntity(
                null, correlationKey, "test-greenhouse", "zone-main", null,
                AssessmentScopeType.ZONE, "zone-main", AssessmentCode.TEMPERATURE_ABOVE_LIMIT,
                AssessmentSeverity.WARNING, AssessmentStatus.ACTIVE,
                "Zone zone-main temperature is above limit.",
                Map.of("actualTemperatureCelsius", 38.2, "maximumTemperatureCelsius", 35.0),
                "temperature-operating-limit", 1,
                now, now, now, null, now, now
        );
    }

    @Test
    void insertsAndFindsActiveAssessment() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        AssessmentEntity saved = assessmentRepository.save(newActiveEntity(correlationKey, now));

        assertThat(saved.getId()).isNotNull();

        List<AssessmentEntity> active = assessmentRepository.findAllByStatus(AssessmentStatus.ACTIVE);
        assertThat(active).extracting(AssessmentEntity::getCorrelationKey).contains(correlationKey);
    }

    @Test
    void partialUniqueIndex_preventsDuplicateActiveCorrelationKeys() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        assessmentRepository.saveAndFlush(newActiveEntity(correlationKey, now));

        assertThatThrownBy(() -> assessmentRepository.saveAndFlush(newActiveEntity(correlationKey, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resolvedAssessment_permitsLaterNewActiveRecord() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        AssessmentEntity resolved = newActiveEntity(correlationKey, now);
        resolved.setStatus(AssessmentStatus.RESOLVED);
        resolved.setResolvedAt(now);
        AssessmentEntity savedResolved = assessmentRepository.saveAndFlush(resolved);

        AssessmentEntity freshActive = newActiveEntity(correlationKey, now.plusSeconds(60));
        AssessmentEntity savedActive = assessmentRepository.saveAndFlush(freshActive);

        assertThat(savedActive.getId()).isNotNull();
        assertThat(savedActive.getId()).isNotEqualTo(savedResolved.getId());
    }

    @Test
    void evidenceJsonRoundTripsCorrectly() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        Long id = assessmentRepository.saveAndFlush(newActiveEntity(correlationKey, now)).getId();
        assessmentRepository.flush();

        AssessmentEntity reloaded = assessmentRepository.findById(id).orElseThrow();

        assertThat(reloaded.getEvidence()).containsEntry("maximumTemperatureCelsius", 35.0);
    }

    @Test
    void enumMappingsWork() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        Long id = assessmentRepository.saveAndFlush(newActiveEntity(correlationKey, now)).getId();

        AssessmentEntity reloaded = assessmentRepository.findById(id).orElseThrow();

        assertThat(reloaded.getScopeType()).isEqualTo(AssessmentScopeType.ZONE);
        assertThat(reloaded.getCode()).isEqualTo(AssessmentCode.TEMPERATURE_ABOVE_LIMIT);
        assertThat(reloaded.getSeverity()).isEqualTo(AssessmentSeverity.WARNING);
        assertThat(reloaded.getStatus()).isEqualTo(AssessmentStatus.ACTIVE);
    }

    @Test
    void findByCorrelationKeyAndStatus_returnsCorrectRecord() {
        String correlationKey = uniqueCorrelationKey();
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        assessmentRepository.saveAndFlush(newActiveEntity(correlationKey, now));

        Optional<AssessmentEntity> found =
                assessmentRepository.findByCorrelationKeyAndStatus(correlationKey, AssessmentStatus.ACTIVE);
        assertThat(found).isPresent();

        Optional<AssessmentEntity> notFoundResolved =
                assessmentRepository.findByCorrelationKeyAndStatus(correlationKey, AssessmentStatus.RESOLVED);
        assertThat(notFoundResolved).isEmpty();
    }
}
