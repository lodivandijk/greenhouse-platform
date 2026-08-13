package com.greenhouse.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByCropIdOrderByCreatedAtAsc(Long cropId);

    List<Goal> findAllByCropIdAndStatusOrderByCreatedAtAsc(Long cropId, GoalStatus status);
}
