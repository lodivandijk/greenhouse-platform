package com.greenhouse.crop;

import com.greenhouse.goal.CreateGoalRequest;
import com.greenhouse.goal.GoalResponse;
import com.greenhouse.goal.GoalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crops")
public class CropController {

    private final CropService cropService;
    private final CropHistoryService cropHistoryService;
    private final HarvestService harvestService;
    private final CropObservationService cropObservationService;
    private final GoalService goalService;

    public CropController(
            CropService cropService,
            CropHistoryService cropHistoryService,
            HarvestService harvestService,
            CropObservationService cropObservationService,
            GoalService goalService
    ) {
        this.cropService = cropService;
        this.cropHistoryService = cropHistoryService;
        this.harvestService = harvestService;
        this.cropObservationService = cropObservationService;
        this.goalService = goalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CropResponse createCrop(@RequestBody CreateCropRequest request) {
        return cropService.createCrop(request.species(), request.variety(), request.locationId(),
                request.plantedAt(), request.notes());
    }

    @GetMapping
    public List<CropResponse> listCrops() {
        return cropService.listCrops();
    }

    @GetMapping("/{cropId}")
    public CropResponse getCrop(@PathVariable Long cropId) {
        return cropService.getCrop(cropId);
    }

    @PatchMapping("/{cropId}")
    public CropResponse updateCrop(@PathVariable Long cropId, @RequestBody UpdateCropRequest request) {
        return cropService.updateCrop(cropId, request.variety(), request.locationId(),
                request.status(), request.notes(), request.endedAt());
    }

    @GetMapping("/{cropId}/history")
    public CropHistoryResponse getCropHistory(@PathVariable Long cropId) {
        return cropHistoryService.getCropHistory(cropId);
    }

    @PostMapping("/{cropId}/harvests")
    @ResponseStatus(HttpStatus.CREATED)
    public HarvestResponse recordHarvest(@PathVariable Long cropId, @RequestBody RecordHarvestRequest request) {
        return harvestService.recordHarvest(cropId, request.harvestedAt(), request.quantity(),
                request.unit(), request.notes());
    }

    @GetMapping("/{cropId}/harvests")
    public List<HarvestResponse> getHarvests(@PathVariable Long cropId) {
        return harvestService.getHarvestHistory(cropId);
    }

    @PostMapping("/{cropId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public CropObservationResponse recordObservation(@PathVariable Long cropId, @RequestBody RecordCropObservationRequest request) {
        return cropObservationService.recordObservation(cropId, request.metric(), request.valueType(),
                request.numericValue(), request.textValue(), request.booleanValue(), request.unit(),
                request.source(), request.confidence(), request.observedAt(), request.notes(), request.metadata());
    }

    @GetMapping("/{cropId}/observations")
    public List<CropObservationResponse> getObservations(@PathVariable Long cropId) {
        return cropObservationService.getObservationHistory(cropId);
    }

    @PostMapping("/{cropId}/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createGoal(@PathVariable Long cropId, @RequestBody CreateGoalRequest request) {
        return goalService.createGoal(cropId, request.goalType(), request.description(),
                request.sourceInstruction(), request.priority());
    }

    @GetMapping("/{cropId}/goals")
    public List<GoalResponse> getGoals(@PathVariable Long cropId) {
        return goalService.listGoalsByCrop(cropId);
    }
}
