package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CropService {

    private final CropRepository cropRepository;
    private final CropMapper cropMapper;
    private final Clock clock;

    public CropService(CropRepository cropRepository, CropMapper cropMapper, Clock clock) {
        this.cropRepository = cropRepository;
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

    Crop findCropOrThrow(Long cropId) {
        if (cropId == null) {
            throw new DomainValidationException("cropId is required.");
        }
        return cropRepository.findById(cropId).orElseThrow(() -> new CropNotFoundException(cropId));
    }
}
