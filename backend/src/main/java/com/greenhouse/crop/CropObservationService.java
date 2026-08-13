package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CropObservationService {

    private final CropObservationRepository cropObservationRepository;
    private final CropService cropService;
    private final CropMapper cropMapper;
    private final Clock clock;

    public CropObservationService(
            CropObservationRepository cropObservationRepository,
            CropService cropService,
            CropMapper cropMapper,
            Clock clock
    ) {
        this.cropObservationRepository = cropObservationRepository;
        this.cropService = cropService;
        this.cropMapper = cropMapper;
        this.clock = clock;
    }

    public CropObservationResponse recordObservation(
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
            Map<String, Object> metadata
    ) {
        cropService.findCropOrThrow(cropId);

        if (metric == null) {
            throw new DomainValidationException("metric is required.");
        }
        if (source == null) {
            throw new DomainValidationException("source is required.");
        }
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new DomainValidationException("confidence must be between 0.0 and 1.0.");
        }
        validateValue(valueType, numericValue, textValue, booleanValue);

        CropObservation observation = new CropObservation();
        observation.setCropId(cropId);
        observation.setMetric(metric);
        observation.setValueType(valueType);
        observation.setNumericValue(numericValue);
        observation.setTextValue(textValue);
        observation.setBooleanValue(booleanValue);
        observation.setUnit(unit);
        observation.setSource(source);
        observation.setConfidence(confidence);
        observation.setObservedAt(observedAt != null ? observedAt : clock.instant());
        observation.setNotes(notes);
        observation.setMetadata(metadata != null ? metadata : Map.of());
        observation.setCreatedAt(clock.instant());

        return cropMapper.toResponse(cropObservationRepository.save(observation));
    }

    public List<CropObservationResponse> getObservationHistory(Long cropId) {
        cropService.findCropOrThrow(cropId);
        return cropObservationRepository.findAllByCropIdOrderByObservedAtAsc(cropId).stream()
                .map(cropMapper::toResponse)
                .toList();
    }

    private void validateValue(CropObservationValueType valueType, Double numericValue, String textValue, Boolean booleanValue) {
        if (valueType == null) {
            throw new DomainValidationException("valueType is required.");
        }

        long providedCount = java.util.stream.Stream.of(numericValue, textValue, booleanValue)
                .filter(java.util.Objects::nonNull)
                .count();
        if (providedCount != 1) {
            throw new DomainValidationException(
                    "Exactly one of numericValue, textValue or booleanValue must be provided.");
        }

        boolean matchesType = switch (valueType) {
            case NUMERIC -> numericValue != null;
            case TEXT -> textValue != null;
            case BOOLEAN -> booleanValue != null;
        };
        if (!matchesType) {
            throw new DomainValidationException(
                    "The provided value does not match valueType " + valueType + ".");
        }
    }
}
