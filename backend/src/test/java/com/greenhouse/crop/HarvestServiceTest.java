package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarvestServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private HarvestRepository harvestRepository;

    @Mock
    private CropService cropService;

    private final CropMapper cropMapper = new CropMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private HarvestService service() {
        return new HarvestService(harvestRepository, cropService, cropMapper, fixedClock);
    }

    @Test
    void recordHarvest_validInput_persists() {
        when(harvestRepository.save(any(Harvest.class))).thenAnswer(invocation -> {
            Harvest harvest = invocation.getArgument(0);
            harvest.setId(10L);
            return harvest;
        });

        HarvestResponse response = service().recordHarvest(1L, null, 180.0, HarvestUnit.GRAMS, "first harvest");

        assertThat(response.quantity()).isEqualTo(180.0);
        assertThat(response.unit()).isEqualTo(HarvestUnit.GRAMS);
        assertThat(response.harvestedAt()).isEqualTo(FIXED_NOW);
        assertThat(response.cropId()).isEqualTo(1L);
    }

    @Test
    void recordHarvest_unknownCrop_propagatesNotFound() {
        when(cropService.findCropOrThrow(99L)).thenThrow(new CropNotFoundException(99L));

        assertThatThrownBy(() -> service().recordHarvest(99L, null, 180.0, HarvestUnit.GRAMS, null))
                .isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void recordHarvest_nonPositiveQuantity_throwsValidationException() {
        assertThatThrownBy(() -> service().recordHarvest(1L, null, 0.0, HarvestUnit.GRAMS, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordHarvest_missingUnit_throwsValidationException() {
        assertThatThrownBy(() -> service().recordHarvest(1L, null, 100.0, null, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
