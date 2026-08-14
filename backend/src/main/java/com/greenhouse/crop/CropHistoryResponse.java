package com.greenhouse.crop;

import com.greenhouse.action.ActionResponse;
import com.greenhouse.goal.GoalResponse;

import java.util.List;

public record CropHistoryResponse(
        CropResponse crop,
        List<GoalResponse> goals,
        List<ActionResponse> actions,
        List<HarvestResponse> harvests,
        List<CropObservationResponse> observations
) {
}
