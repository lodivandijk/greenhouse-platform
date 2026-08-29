package com.greenhouse.crop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropMonitoringProfileRepository extends JpaRepository<CropMonitoringProfile, Long> {

    Optional<CropMonitoringProfile> findByCropIdAndEnabledTrue(Long cropId);

    List<CropMonitoringProfile> findAllByEnabledTrue();

    List<CropMonitoringProfile> findAllByCropIdOrderByVersionDesc(Long cropId);
}
