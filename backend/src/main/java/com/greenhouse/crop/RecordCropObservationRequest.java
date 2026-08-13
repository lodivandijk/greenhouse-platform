package com.greenhouse.crop;

import java.time.Instant;
import java.util.Map;

public record RecordCropObservationRequest(
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
        Map<String, Object> metadata
) {
}
