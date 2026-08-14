package com.greenhouse.action;

import org.springframework.stereotype.Component;

@Component
public class ActionMapper {

    public ActionResponse toResponse(Action entity) {
        return new ActionResponse(
                entity.getId(),
                entity.getCropId(),
                entity.getType(),
                entity.getDescription(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getPerformedAt(),
                entity.getPerformedBy(),
                entity.getCreatedAt()
        );
    }
}
