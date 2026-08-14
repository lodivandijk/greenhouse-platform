package com.greenhouse.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ActionRepository extends JpaRepository<Action, Long> {
    List<Action> findAllByOrderByPerformedAtDesc();

    List<Action> findAllByPerformedAtAfterOrderByPerformedAtDesc(Instant since);

    List<Action> findAllByCropIdOrderByPerformedAtDesc(Long cropId);

    List<Action> findAllByCropIdAndPerformedAtAfterOrderByPerformedAtDesc(Long cropId, Instant since);

    boolean existsByCropId(Long cropId);
}
