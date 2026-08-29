package com.greenhouse.observation.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropSensorAssignmentRepository extends JpaRepository<CropSensorAssignment, Long> {

    Optional<CropSensorAssignment> findBySensorIdAndValidToIsNull(String sensorId);

    Optional<CropSensorAssignment> findByCropIdAndValidToIsNull(Long cropId);

    List<CropSensorAssignment> findAllByValidToIsNull();
}
