package com.greenhouse.crop;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.goal.GoalService;
import com.greenhouse.goal.GoalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DELETE /crops/{cropId}/harvests/{harvestId} ignored cropId entirely and
// deleted by child id alone, so the URL could name one crop while removing
// another crop's record.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class NestedResourceOwnershipTest {

    @Autowired private CropService cropService;
    @Autowired private HarvestService harvestService;
    @Autowired private CropObservationService cropObservationService;
    @Autowired private GoalService goalService;
    @Autowired private CropRepository cropRepository;

    private Long cropA;
    private Long cropB;

    @BeforeEach
    void createTwoCrops() {
        cropA = cropService.createCrop("Basil", null, "planter-owner-a", Instant.now(), null).id();
        cropB = cropService.createCrop("Mint", null, "planter-owner-b", Instant.now(), null).id();
    }

    @AfterEach
    void cleanUp() {
        for (Long cropId : new Long[]{cropA, cropB}) {
            harvestService.getHarvestHistory(cropId)
                    .forEach(harvest -> harvestService.deleteHarvest(harvest.id()));
            cropObservationService.getObservationHistory(cropId)
                    .forEach(observation -> cropObservationService.deleteObservation(observation.id()));
            goalService.listGoalsByCrop(cropId)
                    .forEach(goal -> goalService.deleteGoal(goal.id()));
            cropRepository.findById(cropId).ifPresent(cropRepository::delete);
        }
    }

    @Test
    void aHarvestCannotBeDeletedThroughAnotherCropsUrl() {
        Long harvestOfB = harvestService.recordHarvest(
                cropB, Instant.now(), 12.0, HarvestUnit.GRAMS, null).id();

        assertThatThrownBy(() -> harvestService.deleteHarvest(cropA, harvestOfB))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("does not belong to crop");

        assertThat(harvestService.getHarvestHistory(cropB)).hasSize(1);
    }

    @Test
    void anObservationCannotBeDeletedThroughAnotherCropsUrl() {
        Long observationOfB = cropObservationService.recordObservation(
                cropB, CropObservationMetric.GROWTH_RATE, CropObservationValueType.NUMERIC,
                10.0, null, null, "cm", CropObservationSource.HUMAN, null, Instant.now(), null, null).id();

        assertThatThrownBy(() -> cropObservationService.deleteObservation(cropA, observationOfB))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("does not belong to crop");

        assertThat(cropObservationService.getObservationHistory(cropB)).hasSize(1);
    }

    @Test
    void aGoalCannotBeDeletedThroughAnotherCropsUrl() {
        Long goalOfB = goalService.createGoal(
                cropB, GoalType.MAXIMISE_FOLIAGE, "More mint", "user said so", null).id();

        assertThatThrownBy(() -> goalService.deleteGoal(cropA, goalOfB))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("does not belong to crop");

        assertThat(goalService.listGoalsByCrop(cropB)).hasSize(1);
    }

    @Test
    void theCorrectCropStillDeletesItsOwnRecords() {
        Long harvestOfA = harvestService.recordHarvest(
                cropA, Instant.now(), 5.0, HarvestUnit.GRAMS, null).id();

        harvestService.deleteHarvest(cropA, harvestOfA);

        assertThat(harvestService.getHarvestHistory(cropA)).isEmpty();
    }
}
