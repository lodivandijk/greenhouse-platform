package com.greenhouse.crop;

import com.greenhouse.goal.GoalResponse;

import java.util.List;

public record CropHistoryResponse(
        CropResponse crop,
        List<GoalResponse> goals,
        List<HarvestResponse> harvests,
        List<CropObservationResponse> observations
) {
}
