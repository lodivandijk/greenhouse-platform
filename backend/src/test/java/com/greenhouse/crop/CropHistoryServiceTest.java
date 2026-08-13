package com.greenhouse.crop;

import com.greenhouse.goal.GoalResponse;
import com.greenhouse.goal.GoalService;
import com.greenhouse.goal.GoalStatus;
import com.greenhouse.goal.GoalType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropHistoryServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private CropService cropService;

    @Mock
    private GoalService goalService;

    @Mock
    private HarvestService harvestService;

    @Mock
    private CropObservationService cropObservationService;

    private CropHistoryService service() {
        return new CropHistoryService(cropService, goalService, harvestService, cropObservationService);
    }

    @Test
    void getCropHistory_combinesAllFourSources() {
        CropResponse crop = new CropResponse(1L, "Basil", "Genovese", "planter-02",
                FIXED_NOW, null, CropStatus.PRODUCTIVE, null, FIXED_NOW, FIXED_NOW);
        GoalResponse goal = new GoalResponse(1L, 1L, GoalType.MAXIMISE_FOLIAGE, null,
                GoalStatus.ACTIVE, null, "grow foliage", null, FIXED_NOW, FIXED_NOW);
        HarvestResponse harvest = new HarvestResponse(1L, 1L, FIXED_NOW, 100.0, HarvestUnit.GRAMS, null, FIXED_NOW);
        CropObservationResponse observation = new CropObservationResponse(1L, 1L,
                CropObservationMetric.PLANT_HEALTH, CropObservationValueType.TEXT, null, "healthy", null,
                null, CropObservationSource.HUMAN, null, FIXED_NOW, null, null, FIXED_NOW);

        when(cropService.getCrop(1L)).thenReturn(crop);
        when(goalService.listGoalsByCrop(1L)).thenReturn(List.of(goal));
        when(harvestService.getHarvestHistory(1L)).thenReturn(List.of(harvest));
        when(cropObservationService.getObservationHistory(1L)).thenReturn(List.of(observation));

        CropHistoryResponse history = service().getCropHistory(1L);

        assertThat(history.crop()).isEqualTo(crop);
        assertThat(history.goals()).containsExactly(goal);
        assertThat(history.harvests()).containsExactly(harvest);
        assertThat(history.observations()).containsExactly(observation);
    }
}
