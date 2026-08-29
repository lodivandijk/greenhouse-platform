package com.greenhouse.careloop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareLoopRepository extends JpaRepository<CareLoop, Long> {

    Optional<CareLoop> findByCorrelationKeyAndClosedAtIsNull(String correlationKey);

    List<CareLoop> findAllByClosedAtIsNullOrderByOpenedAtDesc();

    List<CareLoop> findAllByPrimarySubjectTypeAndPrimarySubjectIdOrderByOpenedAtDesc(
            CareLoopSubjectType primarySubjectType, String primarySubjectId);
}
