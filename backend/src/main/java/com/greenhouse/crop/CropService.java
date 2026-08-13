package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.goal.GoalRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CropService {

    private final CropRepository cropRepository;
    private final GoalRepository goalRepository;
    private final HarvestRepository harvestRepository;
    private final CropObservationRepository cropObservationRepository;
    private final CropMapper cropMapper;
    private final Clock clock;

    public CropService(
            CropRepository cropRepository,
            GoalRepository goalRepository,
            HarvestRepository harvestRepository,
            CropObservationRepository cropObservationRepository,
            CropMapper cropMapper,
            Clock clock
    ) {
        this.cropRepository = cropRepository;
        this.goalRepository = goalRepository;
        this.harvestRepository = harvestRepository;
        this.cropObservationRepository = cropObservationRepository;
        this.cropMapper = cropMapper;
        this.clock = clock;
    }

    public CropResponse createCrop(String species, String variety, String locationId, Instant plantedAt, String notes) {
        if (species == null || species.isBlank()) {
            throw new DomainValidationException("species is required.");
        }
        if (locationId == null || locationId.isBlank()) {
            throw new DomainValidationException("location is required.");
        }

        Instant now = clock.instant();
        Crop crop = new Crop();
        crop.setSpecies(species);
        crop.setVariety(variety);
        crop.setLocationId(locationId);
        crop.setPlantedAt(plantedAt != null ? plantedAt : now);
        crop.setStatus(CropStatus.ESTABLISHING);
        crop.setNotes(notes);
        crop.setCreatedAt(now);
        crop.setUpdatedAt(now);

        return cropMapper.toResponse(cropRepository.save(crop));
    }

    public CropResponse updateCrop(
            Long cropId,
            String variety,
            String locationId,
            CropStatus status,
            String notes,
            Instant endedAt
    ) {
        Crop crop = findCropOrThrow(cropId);

        if (variety != null) {
            crop.setVariety(variety);
        }
        if (locationId != null) {
            if (locationId.isBlank()) {
                throw new DomainValidationException("location cannot be blank.");
            }
            crop.setLocationId(locationId);
        }
        if (status != null) {
            crop.setStatus(status);
        }
        if (notes != null) {
            crop.setNotes(notes);
        }
        if (endedAt != null) {
            crop.setEndedAt(endedAt);
        }
        crop.setUpdatedAt(clock.instant());

        return cropMapper.toResponse(cropRepository.save(crop));
    }

    public CropResponse getCrop(Long cropId) {
        return cropMapper.toResponse(findCropOrThrow(cropId));
    }

    public List<CropResponse> listCrops() {
        return cropRepository.findAll().stream().map(cropMapper::toResponse).toList();
    }

    // Hard delete, deliberately scoped to empty crops only - a crop with any recorded
    // goals, harvests or observations must be retired via updateCrop(status=ENDED)
    // instead. This is a cleanup tool for mistakes, not a way to erase real history
    // through a single conversational turn. See ADR-016.
    public CropResponse deleteCrop(Long cropId) {
        Crop crop = findCropOrThrow(cropId);

        boolean hasGoals = goalRepository.existsByCropId(cropId);
        boolean hasHarvests = harvestRepository.existsByCropId(cropId);
        boolean hasObservations = cropObservationRepository.existsByCropId(cropId);
        if (hasGoals || hasHarvests || hasObservations) {
            throw new DomainValidationException(
                    "Crop " + cropId + " has recorded goals, harvests, or observations and cannot be deleted. "
                            + "Retire it instead via update_crop with status ENDED, or delete its individual "
                            + "records first.");
        }

        CropResponse response = cropMapper.toResponse(crop);
        cropRepository.delete(crop);
        return response;
    }

    Crop findCropOrThrow(Long cropId) {
        if (cropId == null) {
            throw new DomainValidationException("cropId is required.");
        }
        return cropRepository.findById(cropId).orElseThrow(() -> new CropNotFoundException(cropId));
    }
}
