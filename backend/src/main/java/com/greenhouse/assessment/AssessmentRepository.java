package com.greenhouse.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<AssessmentEntity, Long> {

    List<AssessmentEntity> findAllByStatus(AssessmentStatus status);

    Optional<AssessmentEntity> findByCorrelationKeyAndStatus(String correlationKey, AssessmentStatus status);

    List<AssessmentEntity> findAllByGreenhouseIdAndStatus(String greenhouseId, AssessmentStatus status);
}
