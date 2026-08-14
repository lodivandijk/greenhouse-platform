package com.greenhouse.crop;

import com.greenhouse.action.ActionService;
import com.greenhouse.goal.GoalService;
import org.springframework.stereotype.Service;

@Service
public class CropHistoryService {

    private final CropService cropService;
    private final GoalService goalService;
    private final ActionService actionService;
    private final HarvestService harvestService;
    private final CropObservationService cropObservationService;

    public CropHistoryService(
            CropService cropService,
            GoalService goalService,
            ActionService actionService,
            HarvestService harvestService,
            CropObservationService cropObservationService
    ) {
        this.cropService = cropService;
        this.goalService = goalService;
        this.actionService = actionService;
        this.harvestService = harvestService;
        this.cropObservationService = cropObservationService;
    }

    public CropHistoryResponse getCropHistory(Long cropId) {
        CropResponse crop = cropService.getCrop(cropId);

        return new CropHistoryResponse(
                crop,
                goalService.listGoalsByCrop(cropId),
                actionService.listActions(cropId, null, null),
                harvestService.getHarvestHistory(cropId),
                cropObservationService.getObservationHistory(cropId)
        );
    }
}
