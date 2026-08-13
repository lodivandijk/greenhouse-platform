package com.greenhouse.crop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropRepository extends JpaRepository<Crop, Long> {
    List<Crop> findAllByStatus(CropStatus status);
}
