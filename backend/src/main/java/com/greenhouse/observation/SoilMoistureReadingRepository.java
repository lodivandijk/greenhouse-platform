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

    // The state of the soil immediately BEFORE work was done. Without this
    // there is nothing to attribute a change to, and drift between two later
    // readings gets credited to the watering (ADR-026).
    Optional<SoilMoistureReadingEntity> findFirstBySensorIdAndReceivedAtLessThanEqualOrderByReceivedAtDesc(
            String sensorId, Instant at);

    // Bounded at both ends, so the stored evaluation window is actually the
    // window that was evaluated.
    List<SoilMoistureReadingEntity> findAllBySensorIdAndReceivedAtBetweenOrderByReceivedAtAsc(
            String sensorId, Instant from, Instant to);

    // One query for every sensor's latest reading, rather than N per-sensor
    // lookups - the twin is assembled on every scheduler tick and every state
    // request, so this stays a single round trip as sensor count grows.
    @Query(value = "SELECT DISTINCT ON (sensor_id) * FROM soil_moisture_reading "
            + "ORDER BY sensor_id, received_at DESC", nativeQuery = true)
    List<SoilMoistureReadingEntity> findLatestPerSensor();
}
