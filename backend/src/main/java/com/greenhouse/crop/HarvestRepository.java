package com.greenhouse.crop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HarvestRepository extends JpaRepository<Harvest, Long> {
    List<Harvest> findAllByCropIdOrderByHarvestedAtAsc(Long cropId);
}
