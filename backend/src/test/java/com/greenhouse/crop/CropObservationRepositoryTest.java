package com.greenhouse.crop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CropObservationRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private CropObservationRepository cropObservationRepository;

    private Long saveCrop() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        Crop crop = new Crop();
        crop.setSpecies("basil-" + UUID.randomUUID());
        crop.setLocationId("planter-01");
        crop.setPlantedAt(now);
        crop.setStatus(CropStatus.PRODUCTIVE);
        crop.setCreatedAt(now);
        crop.setUpdatedAt(now);
        return cropRepository.save(crop).getId();
    }

    private static CropObservation newObservation(Long cropId, Instant observedAt, String textValue) {
        CropObservation observation = new CropObservation();
        observation.setCropId(cropId);
        observation.setMetric(CropObservationMetric.PLANT_HEALTH);
        observation.setValueType(CropObservationValueType.TEXT);
        observation.setTextValue(textValue);
        observation.setSource(CropObservationSource.HUMAN);
        observation.setObservedAt(observedAt);
        observation.setMetadata(Map.of("note", "test"));
        observation.setCreatedAt(observedAt);
        return observation;
    }

    @Test
    void findAllByCropId_returnsInChronologicalOrder() {
        Long cropId = saveCrop();

        cropObservationRepository.save(newObservation(cropId, Instant.parse("2026-08-10T12:00:00Z"), "healthy"));
        cropObservationRepository.save(newObservation(cropId, Instant.parse("2026-08-05T12:00:00Z"), "establishing"));
        cropObservationRepository.save(newObservation(cropId, Instant.parse("2026-08-12T12:00:00Z"), "thriving"));

        List<CropObservation> observations = cropObservationRepository.findAllByCropIdOrderByObservedAtAsc(cropId);

        assertThat(observations).extracting(CropObservation::getTextValue)
                .containsExactly("establishing", "healthy", "thriving");
    }

    @Test
    void metadataJson_roundTripsCorrectly() {
        Long cropId = saveCrop();
        Instant observedAt = Instant.parse("2026-08-13T09:00:00Z");

        Long id = cropObservationRepository.save(newObservation(cropId, observedAt, "healthy")).getId();
        cropObservationRepository.flush();

        CropObservation reloaded = cropObservationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getMetadata()).containsEntry("note", "test");
        assertThat(reloaded.getMetric()).isEqualTo(CropObservationMetric.PLANT_HEALTH);
        assertThat(reloaded.getValueType()).isEqualTo(CropObservationValueType.TEXT);
        assertThat(reloaded.getSource()).isEqualTo(CropObservationSource.HUMAN);
    }

    @Test
    void numericValueType_roundTripsCorrectly() {
        Long cropId = saveCrop();
        Instant observedAt = Instant.parse("2026-08-13T09:00:00Z");

        CropObservation observation = new CropObservation();
        observation.setCropId(cropId);
        observation.setMetric(CropObservationMetric.FLOWER_COUNT);
        observation.setValueType(CropObservationValueType.NUMERIC);
        observation.setNumericValue(12.0);
        observation.setSource(CropObservationSource.HUMAN);
        observation.setObservedAt(observedAt);
        observation.setMetadata(Map.of());
        observation.setCreatedAt(observedAt);

        Long id = cropObservationRepository.save(observation).getId();

        CropObservation reloaded = cropObservationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getNumericValue()).isEqualTo(12.0);
        assertThat(reloaded.getTextValue()).isNull();
    }
}
