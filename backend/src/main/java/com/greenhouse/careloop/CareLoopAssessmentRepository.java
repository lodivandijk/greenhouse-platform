package com.greenhouse.careloop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareLoopAssessmentRepository extends JpaRepository<CareLoopAssessment, Long> {

    List<CareLoopAssessment> findAllByCareLoopId(Long careLoopId);

    List<CareLoopAssessment> findAllByAssessmentId(Long assessmentId);

    boolean existsByCareLoopIdAndAssessmentId(Long careLoopId, Long assessmentId);
}
