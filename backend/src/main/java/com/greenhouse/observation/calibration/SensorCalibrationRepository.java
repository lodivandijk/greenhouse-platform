package com.greenhouse.observation.calibration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorCalibrationRepository extends JpaRepository<SensorCalibration, Long> {

    Optional<SensorCalibration> findBySensorIdAndValidToIsNull(String sensorId);

    List<SensorCalibration> findAllByValidToIsNull();

    List<SensorCalibration> findAllBySensorIdOrderByVersionDesc(String sensorId);
}
