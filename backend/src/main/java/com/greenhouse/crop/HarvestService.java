package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class HarvestService {

    private final HarvestRepository harvestRepository;
    private final CropService cropService;
    private final CropMapper cropMapper;
    private final Clock clock;

    public HarvestService(HarvestRepository harvestRepository, CropService cropService, CropMapper cropMapper, Clock clock) {
        this.harvestRepository = harvestRepository;
        this.cropService = cropService;
        this.cropMapper = cropMapper;
        this.clock = clock;
    }

    public HarvestResponse recordHarvest(Long cropId, Instant harvestedAt, Double quantity, HarvestUnit unit, String notes) {
        cropService.findCropOrThrow(cropId);

        if (quantity == null || quantity <= 0) {
            throw new DomainValidationException("quantity must be a positive number.");
        }
        if (unit == null) {
            throw new DomainValidationException("unit is required.");
        }

        Harvest harvest = new Harvest();
        harvest.setCropId(cropId);
        harvest.setHarvestedAt(harvestedAt != null ? harvestedAt : clock.instant());
        harvest.setQuantity(quantity);
        harvest.setUnit(unit);
        harvest.setNotes(notes);
        harvest.setCreatedAt(clock.instant());

        return cropMapper.toResponse(harvestRepository.save(harvest));
    }

    public List<HarvestResponse> getHarvestHistory(Long cropId) {
        cropService.findCropOrThrow(cropId);
        return harvestRepository.findAllByCropIdOrderByHarvestedAtAsc(cropId).stream()
                .map(cropMapper::toResponse)
                .toList();
    }

    // The nested route DELETE /crops/{cropId}/harvests/{harvestId} names a parent,
    // so the parent must be true. Without this the path segment was decorative
    // and a caller could address one crop while deleting another crop's record.
    public HarvestResponse deleteHarvest(Long cropId, Long harvestId) {
        Harvest harvest = requireHarvest(harvestId);
        if (cropId != null && !cropId.equals(harvest.getCropId())) {
            throw new DomainValidationException(
                    "Harvest " + harvestId + " does not belong to crop " + cropId + ".");
        }
        return deleteHarvest(harvestId);
    }

    private Harvest requireHarvest(Long harvestId) {
        return harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestNotFoundException(harvestId));
    }

    public HarvestResponse deleteHarvest(Long harvestId) {
        Harvest harvest = requireHarvest(harvestId);
        HarvestResponse response = cropMapper.toResponse(harvest);
        harvestRepository.delete(harvest);
        return response;
    }
}
