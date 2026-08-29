package com.greenhouse.observation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SoilMoistureReadingRepository extends JpaRepository<SoilMoistureReadingEntity, Long> {

    List<SoilMoistureReadingEntity> findAllByOrderByReceivedAtDesc();

    List<SoilMoistureReadingEntity> findAllBySensorIdOrderByReceivedAtDesc(String sensorId);

    Optional<SoilMoistureReadingEntity> findFirstBySensorIdOrderByReceivedAtDesc(String sensorId);

    List<SoilMoistureReadingEntity> findAllBySensorIdAndReceivedAtAfterOrderByReceivedAtDesc(
            String sensorId, Instant since);

    // One query for every sensor's latest reading, rather than N per-sensor
    // lookups - the twin is assembled on every scheduler tick and every state
    // request, so this stays a single round trip as sensor count grows.
    @Query(value = "SELECT DISTINCT ON (sensor_id) * FROM soil_moisture_reading "
            + "ORDER BY sensor_id, received_at DESC", nativeQuery = true)
    List<SoilMoistureReadingEntity> findLatestPerSensor();
}
