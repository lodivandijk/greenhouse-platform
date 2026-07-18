package com.greenhouse.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {

    Optional<ObservationEntity> findFirstByDeviceIdOrderByReceivedAtDesc(String deviceId);

    Optional<ObservationEntity> findFirstByOrderByReceivedAtDesc();

    List<ObservationEntity> findAllByOrderByReceivedAtDesc();
}
