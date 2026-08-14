package com.greenhouse.crop;

import com.greenhouse.action.ActionRepository;
import com.greenhouse.common.DomainValidationException;
import com.greenhouse.goal.GoalRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private CropRepository cropRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private HarvestRepository harvestRepository;

    @Mock
    private CropObservationRepository cropObservationRepository;

    @Mock
    private ActionRepository actionRepository;

    private final CropMapper cropMapper = new CropMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private CropService service() {
        return new CropService(cropRepository, goalRepository, harvestRepository, cropObservationRepository,
                actionRepository, cropMapper, fixedClock);
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

    @Test
    void deleteCrop_emptyCrop_deletesAndReturnsIt() {
        Crop crop = new Crop();
        crop.setId(7L);
        crop.setSpecies("Basil");
        crop.setLocationId("planter-02");
        crop.setStatus(CropStatus.ESTABLISHING);
        crop.setCreatedAt(FIXED_NOW);
        crop.setUpdatedAt(FIXED_NOW);

        when(cropRepository.findById(7L)).thenReturn(Optional.of(crop));
        when(goalRepository.existsByCropId(7L)).thenReturn(false);
        when(harvestRepository.existsByCropId(7L)).thenReturn(false);
        when(cropObservationRepository.existsByCropId(7L)).thenReturn(false);
        when(actionRepository.existsByCropId(7L)).thenReturn(false);

        CropResponse response = service().deleteCrop(7L);

        assertThat(response.id()).isEqualTo(7L);
        verify(cropRepository).delete(crop);
    }

    @Test
    void deleteCrop_withGoals_throwsValidationExceptionAndDoesNotDelete() {
        Crop crop = new Crop();
        crop.setId(8L);
        crop.setSpecies("Basil");
        crop.setLocationId("planter-02");
        crop.setStatus(CropStatus.PRODUCTIVE);

        when(cropRepository.findById(8L)).thenReturn(Optional.of(crop));
        when(goalRepository.existsByCropId(8L)).thenReturn(true);
        lenient().when(harvestRepository.existsByCropId(8L)).thenReturn(false);
        lenient().when(cropObservationRepository.existsByCropId(8L)).thenReturn(false);
        lenient().when(actionRepository.existsByCropId(8L)).thenReturn(false);

        assertThatThrownBy(() -> service().deleteCrop(8L))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("8");
    }

    @Test
    void deleteCrop_withHarvests_throwsValidationException() {
        Crop crop = new Crop();
        crop.setId(9L);
        crop.setSpecies("Basil");
        crop.setLocationId("planter-02");
        crop.setStatus(CropStatus.PRODUCTIVE);

        when(cropRepository.findById(9L)).thenReturn(Optional.of(crop));
        lenient().when(goalRepository.existsByCropId(9L)).thenReturn(false);
        when(harvestRepository.existsByCropId(9L)).thenReturn(true);
        lenient().when(cropObservationRepository.existsByCropId(9L)).thenReturn(false);
        lenient().when(actionRepository.existsByCropId(9L)).thenReturn(false);

        assertThatThrownBy(() -> service().deleteCrop(9L))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void deleteCrop_withObservations_throwsValidationException() {
        Crop crop = new Crop();
        crop.setId(10L);
        crop.setSpecies("Basil");
        crop.setLocationId("planter-02");
        crop.setStatus(CropStatus.PRODUCTIVE);

        when(cropRepository.findById(10L)).thenReturn(Optional.of(crop));
        lenient().when(goalRepository.existsByCropId(10L)).thenReturn(false);
        lenient().when(harvestRepository.existsByCropId(10L)).thenReturn(false);
        when(cropObservationRepository.existsByCropId(10L)).thenReturn(true);
        lenient().when(actionRepository.existsByCropId(10L)).thenReturn(false);

        assertThatThrownBy(() -> service().deleteCrop(10L))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void deleteCrop_withActions_throwsValidationException() {
        Crop crop = new Crop();
        crop.setId(11L);
        crop.setSpecies("Strawberry");
        crop.setLocationId("pot-2");
        crop.setStatus(CropStatus.PRODUCTIVE);

        when(cropRepository.findById(11L)).thenReturn(Optional.of(crop));
        lenient().when(goalRepository.existsByCropId(11L)).thenReturn(false);
        lenient().when(harvestRepository.existsByCropId(11L)).thenReturn(false);
        lenient().when(cropObservationRepository.existsByCropId(11L)).thenReturn(false);
        when(actionRepository.existsByCropId(11L)).thenReturn(true);

        assertThatThrownBy(() -> service().deleteCrop(11L))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("11");
    }

    @Test
    void deleteCrop_unknownCrop_throwsNotFound() {
        when(cropRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteCrop(404L))
                .isInstanceOf(CropNotFoundException.class);
    }
}
