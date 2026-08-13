package com.greenhouse.crop;

import org.springframework.stereotype.Component;

@Component
public class CropMapper {

    public CropResponse toResponse(Crop entity) {
        return new CropResponse(
                entity.getId(),
                entity.getSpecies(),
                entity.getVariety(),
                entity.getLocationId(),
                entity.getPlantedAt(),
                entity.getEndedAt(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public HarvestResponse toResponse(Harvest entity) {
        return new HarvestResponse(
                entity.getId(),
                entity.getCropId(),
                entity.getHarvestedAt(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }

    public CropObservationResponse toResponse(CropObservation entity) {
        return new CropObservationResponse(
                entity.getId(),
                entity.getCropId(),
                entity.getMetric(),
                entity.getValueType(),
                entity.getNumericValue(),
                entity.getTextValue(),
                entity.getBooleanValue(),
                entity.getUnit(),
                entity.getSource(),
                entity.getConfidence(),
                entity.getObservedAt(),
                entity.getNotes(),
                entity.getMetadata(),
                entity.getCreatedAt()
        );
    }
}
