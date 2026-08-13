package com.greenhouse.crop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CropRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    private static Crop newCrop(String species, String locationId, CropStatus status, Instant now) {
        Crop crop = new Crop();
        crop.setSpecies(species);
        crop.setVariety("Genovese");
        crop.setLocationId(locationId);
        crop.setPlantedAt(now);
        crop.setStatus(status);
        crop.setNotes("test crop");
        crop.setCreatedAt(now);
        crop.setUpdatedAt(now);
        return crop;
    }

    @Test
    void insertsAndFindsCrop() {
        Instant now = Instant.parse("2026-08-13T12:00:00Z");
        String species = "basil-" + UUID.randomUUID();

        Crop saved = cropRepository.save(newCrop(species, "planter-01", CropStatus.ESTABLISHING, now));

        assertThat(saved.getId()).isNotNull();

        Crop reloaded = cropRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSpecies()).isEqualTo(species);
        assertThat(reloaded.getVariety()).isEqualTo("Genovese");
        assertThat(reloaded.getLocationId()).isEqualTo("planter-01");
        assertThat(reloaded.getStatus()).isEqualTo(CropStatus.ESTABLISHING);
    }

    @Test
    void findAllByStatus_filtersCorrectly() {
        Instant now = Instant.parse("2026-08-13T12:00:00Z");
        String marker = UUID.randomUUID().toString();

        Crop productive = cropRepository.save(newCrop("crop-productive-" + marker, "planter-02", CropStatus.PRODUCTIVE, now));
        cropRepository.save(newCrop("crop-planned-" + marker, "planter-03", CropStatus.PLANNED, now));

        List<Crop> productiveCrops = cropRepository.findAllByStatus(CropStatus.PRODUCTIVE);

        assertThat(productiveCrops).extracting(Crop::getId).contains(productive.getId());
        assertThat(productiveCrops).extracting(Crop::getSpecies)
                .doesNotContain("crop-planned-" + marker);
    }

    @Test
    void endedAt_persistsAndReloads() {
        Instant plantedAt = Instant.parse("2026-08-01T12:00:00Z");
        Instant endedAt = Instant.parse("2026-08-13T12:00:00Z");
        Crop crop = newCrop("ended-crop-" + UUID.randomUUID(), "planter-04", CropStatus.ENDED, plantedAt);
        crop.setEndedAt(endedAt);

        Long id = cropRepository.save(crop).getId();

        Crop reloaded = cropRepository.findById(id).orElseThrow();
        assertThat(reloaded.getEndedAt()).isEqualTo(endedAt);
    }
}
