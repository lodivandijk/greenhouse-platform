package com.greenhouse.briefing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyBriefingSnapshotRepository extends JpaRepository<DailyBriefingSnapshot, Long> {

    Optional<DailyBriefingSnapshot> findFirstByGreenhouseDayOrderByGeneratedAtDescIdDesc(LocalDate greenhouseDay);

    Optional<DailyBriefingSnapshot> findFirstByOrderByGeneratedAtDescIdDesc();

    List<DailyBriefingSnapshot> findAllByGreenhouseDayOrderByGeneratedAtAsc(LocalDate greenhouseDay);

    boolean existsByGreenhouseDay(LocalDate greenhouseDay);
}
