package com.greenhouse.crop;

import java.time.Instant;
import java.util.Map;

public record CropObservationResponse(
        Long id,
        Long cropId,
        CropObservationMetric metric,
        CropObservationValueType valueType,
        Double numericValue,
        String textValue,
        Boolean booleanValue,
        String unit,
        CropObservationSource source,
        Double confidence,
        Instant observedAt,
        String notes,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
