package com.greenhouse.goal;

import org.springframework.stereotype.Component;

@Component
public class GoalMapper {

    public GoalResponse toResponse(Goal entity) {
        return new GoalResponse(
                entity.getId(),
                entity.getCropId(),
                entity.getGoalType(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getSourceInstruction(),
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
