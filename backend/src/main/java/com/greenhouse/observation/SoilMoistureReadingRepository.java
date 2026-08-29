package com.greenhouse.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoilMoistureReadingRepository extends JpaRepository<SoilMoistureReadingEntity, Long> {

    List<SoilMoistureReadingEntity> findAllByOrderByReceivedAtDesc();

    List<SoilMoistureReadingEntity> findAllBySensorIdOrderByReceivedAtDesc(String sensorId);

    Optional<SoilMoistureReadingEntity> findFirstBySensorIdOrderByReceivedAtDesc(String sensorId);
}
