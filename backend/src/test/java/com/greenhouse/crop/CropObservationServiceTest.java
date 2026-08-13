package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropObservationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private CropObservationRepository cropObservationRepository;

    @Mock
    private CropService cropService;

    private final CropMapper cropMapper = new CropMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private CropObservationService service() {
        return new CropObservationService(cropObservationRepository, cropService, cropMapper, fixedClock);
    }

    @Test
    void recordObservation_validNumericValue_persists() {
        when(cropObservationRepository.save(any(CropObservation.class))).thenAnswer(invocation -> {
            CropObservation observation = invocation.getArgument(0);
            observation.setId(1L);
            return observation;
        });

        CropObservationResponse response = service().recordObservation(
                1L, CropObservationMetric.FLOWER_COUNT, CropObservationValueType.NUMERIC,
                12.0, null, null, "count", CropObservationSource.HUMAN, null, null, null, null
        );

        assertThat(response.numericValue()).isEqualTo(12.0);
        assertThat(response.metric()).isEqualTo(CropObservationMetric.FLOWER_COUNT);
        assertThat(response.observedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void recordObservation_textValueWithoutOthers_persists() {
        when(cropObservationRepository.save(any(CropObservation.class))).thenAnswer(invocation -> {
            CropObservation observation = invocation.getArgument(0);
            observation.setId(2L);
            return observation;
        });

        CropObservationResponse response = service().recordObservation(
                1L, CropObservationMetric.PLANT_HEALTH, CropObservationValueType.TEXT,
                null, "healthy", null, null, CropObservationSource.HUMAN, 0.9, FIXED_NOW, "looks great", null
        );

        assertThat(response.textValue()).isEqualTo("healthy");
        assertThat(response.confidence()).isEqualTo(0.9);
    }

    @Test
    void recordObservation_mismatchedValueType_throwsValidationException() {
        assertThatThrownBy(() -> service().recordObservation(
                1L, CropObservationMetric.FLOWER_COUNT, CropObservationValueType.NUMERIC,
                null, "twelve", null, null, CropObservationSource.HUMAN, null, null, null, null
        )).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordObservation_multipleValuesProvided_throwsValidationException() {
        assertThatThrownBy(() -> service().recordObservation(
                1L, CropObservationMetric.PLANT_HEALTH, CropObservationValueType.TEXT,
                5.0, "healthy", null, null, CropObservationSource.HUMAN, null, null, null, null
        )).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordObservation_confidenceOutOfRange_throwsValidationException() {
        assertThatThrownBy(() -> service().recordObservation(
                1L, CropObservationMetric.PLANT_HEALTH, CropObservationValueType.TEXT,
                null, "healthy", null, null, CropObservationSource.HUMAN, 1.5, null, null, null
        )).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordObservation_missingMetric_throwsValidationException() {
        assertThatThrownBy(() -> service().recordObservation(
                1L, null, CropObservationValueType.TEXT,
                null, "healthy", null, null, CropObservationSource.HUMAN, null, null, null, null
        )).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordObservation_unknownCrop_propagatesNotFound() {
        when(cropService.findCropOrThrow(99L)).thenThrow(new CropNotFoundException(99L));

        assertThatThrownBy(() -> service().recordObservation(
                99L, CropObservationMetric.PLANT_HEALTH, CropObservationValueType.TEXT,
                null, "healthy", null, null, CropObservationSource.HUMAN, null, null, null, null
        )).isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void deleteObservation_existingObservation_deletesAndReturnsIt() {
        CropObservation observation = new CropObservation();
        observation.setId(21L);
        observation.setCropId(1L);
        observation.setMetric(CropObservationMetric.PLANT_HEALTH);
        observation.setValueType(CropObservationValueType.TEXT);
        observation.setTextValue("healthy");
        observation.setSource(CropObservationSource.HUMAN);

        when(cropObservationRepository.findById(21L)).thenReturn(Optional.of(observation));

        CropObservationResponse response = service().deleteObservation(21L);

        assertThat(response.id()).isEqualTo(21L);
        verify(cropObservationRepository).delete(observation);
    }

    @Test
    void deleteObservation_unknownObservation_throwsNotFound() {
        when(cropObservationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteObservation(404L))
                .isInstanceOf(CropObservationNotFoundException.class)
                .hasMessageContaining("404");
    }
}
