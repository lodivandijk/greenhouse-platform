package com.greenhouse.crop;

import com.greenhouse.goal.GoalService;
import org.springframework.stereotype.Service;

@Service
public class CropHistoryService {

    private final CropService cropService;
    private final GoalService goalService;
    private final HarvestService harvestService;
    private final CropObservationService cropObservationService;

    public CropHistoryService(
            CropService cropService,
            GoalService goalService,
            HarvestService harvestService,
            CropObservationService cropObservationService
    ) {
        this.cropService = cropService;
        this.goalService = goalService;
        this.harvestService = harvestService;
        this.cropObservationService = cropObservationService;
    }

    public CropHistoryResponse getCropHistory(Long cropId) {
        CropResponse crop = cropService.getCrop(cropId);

        return new CropHistoryResponse(
                crop,
                goalService.listGoalsByCrop(cropId),
                harvestService.getHarvestHistory(cropId),
                cropObservationService.getObservationHistory(cropId)
        );
    }
}
