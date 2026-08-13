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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private CropRepository cropRepository;

    private final CropMapper cropMapper = new CropMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private CropService service() {
        return new CropService(cropRepository, cropMapper, fixedClock);
    }

    @Test
    void createCrop_persistsWithEstablishingStatus() {
        when(cropRepository.save(any(Crop.class))).thenAnswer(invocation -> {
            Crop crop = invocation.getArgument(0);
            crop.setId(1L);
            return crop;
        });

        CropResponse response = service().createCrop("Basil", "Genovese", "planter-02", null, "first crop");

        assertThat(response.species()).isEqualTo("Basil");
        assertThat(response.locationId()).isEqualTo("planter-02");
        assertThat(response.status()).isEqualTo(CropStatus.ESTABLISHING);
        assertThat(response.plantedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void createCrop_blankSpecies_throwsValidationException() {
        assertThatThrownBy(() -> service().createCrop("  ", null, "planter-02", null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void createCrop_blankLocation_throwsValidationException() {
        assertThatThrownBy(() -> service().createCrop("Basil", null, "", null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void updateCrop_appliesEndedStatusAndTimestamp() {
        Crop existing = new Crop();
        existing.setId(5L);
        existing.setSpecies("Basil");
        existing.setLocationId("planter-02");
        existing.setStatus(CropStatus.PRODUCTIVE);
        existing.setCreatedAt(FIXED_NOW.minusSeconds(1000));
        existing.setUpdatedAt(FIXED_NOW.minusSeconds(1000));

        when(cropRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(cropRepository.save(any(Crop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant endedAt = FIXED_NOW;
        CropResponse response = service().updateCrop(5L, null, null, CropStatus.ENDED, "final notes", endedAt);

        assertThat(response.status()).isEqualTo(CropStatus.ENDED);
        assertThat(response.endedAt()).isEqualTo(endedAt);
        assertThat(response.notes()).isEqualTo("final notes");
        assertThat(response.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void updateCrop_unknownCrop_throwsNotFound() {
        when(cropRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateCrop(99L, "Thai", null, null, null, null))
                .isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void getCrop_unknownCrop_throwsNotFound() {
        when(cropRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getCrop(42L))
                .isInstanceOf(CropNotFoundException.class)
                .hasMessageContaining("42");
    }
}
