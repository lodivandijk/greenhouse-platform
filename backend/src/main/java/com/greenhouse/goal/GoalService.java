package com.greenhouse.goal;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.crop.CropNotFoundException;
import com.greenhouse.crop.CropRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final CropRepository cropRepository;
    private final GoalMapper goalMapper;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, CropRepository cropRepository, GoalMapper goalMapper, Clock clock) {
        this.goalRepository = goalRepository;
        this.cropRepository = cropRepository;
        this.goalMapper = goalMapper;
        this.clock = clock;
    }

    public GoalResponse createGoal(Long cropId, GoalType goalType, String description, String sourceInstruction, Integer priority) {
        if (cropId == null) {
            throw new DomainValidationException("cropId is required.");
        }
        if (!cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }
        if (goalType == null) {
            throw new DomainValidationException("goalType is required.");
        }
        if (goalType == GoalType.OTHER && (description == null || description.isBlank())) {
            throw new DomainValidationException("description is required when goalType is OTHER.");
        }

        Instant now = clock.instant();

        Goal goal = new Goal();
        goal.setCropId(cropId);
        goal.setGoalType(goalType);
        goal.setDescription(description);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setPriority(priority);
        goal.setSourceInstruction(sourceInstruction);
        goal.setMetadata(Map.of());
        goal.setCreatedAt(now);
        goal.setUpdatedAt(now);

        return goalMapper.toResponse(goalRepository.save(goal));
    }

    public List<GoalResponse> listGoalsByCrop(Long cropId) {
        if (!cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }
        return goalRepository.findAllByCropIdOrderByCreatedAtAsc(cropId).stream()
                .map(goalMapper::toResponse)
                .toList();
    }

    public List<GoalResponse> listActiveGoalsByCrop(Long cropId) {
        if (!cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }
        return goalRepository.findAllByCropIdAndStatusOrderByCreatedAtAsc(cropId, GoalStatus.ACTIVE).stream()
                .map(goalMapper::toResponse)
                .toList();
    }
}
