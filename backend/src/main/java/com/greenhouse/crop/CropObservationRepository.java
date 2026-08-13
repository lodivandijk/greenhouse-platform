package com.greenhouse.crop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropObservationRepository extends JpaRepository<CropObservation, Long> {
    List<CropObservation> findAllByCropIdOrderByObservedAtAsc(Long cropId);

    boolean existsByCropId(Long cropId);
}
