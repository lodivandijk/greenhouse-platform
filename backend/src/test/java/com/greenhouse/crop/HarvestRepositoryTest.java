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
class HarvestRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private HarvestRepository harvestRepository;

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

    private static Harvest newHarvest(Long cropId, Instant harvestedAt, double quantity) {
        Harvest harvest = new Harvest();
        harvest.setCropId(cropId);
        harvest.setHarvestedAt(harvestedAt);
        harvest.setQuantity(quantity);
        harvest.setUnit(HarvestUnit.GRAMS);
        harvest.setCreatedAt(harvestedAt);
        return harvest;
    }

    @Test
    void findAllByCropId_returnsInChronologicalOrder() {
        Long cropId = saveCrop();

        harvestRepository.save(newHarvest(cropId, Instant.parse("2026-08-10T12:00:00Z"), 100.0));
        harvestRepository.save(newHarvest(cropId, Instant.parse("2026-08-05T12:00:00Z"), 50.0));
        harvestRepository.save(newHarvest(cropId, Instant.parse("2026-08-12T12:00:00Z"), 180.0));

        List<Harvest> harvests = harvestRepository.findAllByCropIdOrderByHarvestedAtAsc(cropId);

        assertThat(harvests).extracting(Harvest::getQuantity)
                .containsExactly(50.0, 100.0, 180.0);
    }

    @Test
    void quantityAndUnit_roundTripCorrectly() {
        Long cropId = saveCrop();
        Instant harvestedAt = Instant.parse("2026-08-13T09:00:00Z");

        Long id = harvestRepository.save(newHarvest(cropId, harvestedAt, 180.0)).getId();

        Harvest reloaded = harvestRepository.findById(id).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(180.0);
        assertThat(reloaded.getUnit()).isEqualTo(HarvestUnit.GRAMS);
        assertThat(reloaded.getHarvestedAt()).isEqualTo(harvestedAt);
    }
}
