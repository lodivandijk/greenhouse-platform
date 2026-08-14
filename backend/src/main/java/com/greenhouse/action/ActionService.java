package com.greenhouse.action;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.crop.CropNotFoundException;
import com.greenhouse.crop.CropRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ActionService {

    private final ActionRepository actionRepository;
    private final CropRepository cropRepository;
    private final ActionMapper actionMapper;
    private final Clock clock;

    public ActionService(ActionRepository actionRepository, CropRepository cropRepository, ActionMapper actionMapper, Clock clock) {
        this.actionRepository = actionRepository;
        this.cropRepository = cropRepository;
        this.actionMapper = actionMapper;
        this.clock = clock;
    }

    public ActionResponse recordAction(
            Long cropId,
            ActionType type,
            String description,
            Double quantity,
            String unit,
            Instant performedAt,
            ActionPerformedBy performedBy
    ) {
        if (cropId == null) {
            throw new DomainValidationException("cropId is required.");
        }
        if (!cropRepository.existsById(cropId)) {
            throw new CropNotFoundException(cropId);
        }
        if (type == null) {
            throw new DomainValidationException("type is required.");
        }
        if (quantity != null && (unit == null || unit.isBlank())) {
            throw new DomainValidationException("unit is required when quantity is provided.");
        }

        Instant now = clock.instant();

        Action action = new Action();
        action.setCropId(cropId);
        action.setType(type);
        action.setDescription(description);
        action.setQuantity(quantity);
        action.setUnit(unit);
        action.setPerformedAt(performedAt != null ? performedAt : now);
        action.setPerformedBy(performedBy != null ? performedBy : ActionPerformedBy.HUMAN);
        action.setCreatedAt(now);

        return actionMapper.toResponse(actionRepository.save(action));
    }

    public List<ActionResponse> listActions(Long cropId, Integer limit, Instant since) {
        if (limit != null && limit <= 0) {
            throw new DomainValidationException("limit must be a positive number.");
        }

        List<Action> actions;
        if (cropId != null) {
            if (!cropRepository.existsById(cropId)) {
                throw new CropNotFoundException(cropId);
            }
            actions = since != null
                    ? actionRepository.findAllByCropIdAndPerformedAtAfterOrderByPerformedAtDesc(cropId, since)
                    : actionRepository.findAllByCropIdOrderByPerformedAtDesc(cropId);
        } else {
            actions = since != null
                    ? actionRepository.findAllByPerformedAtAfterOrderByPerformedAtDesc(since)
                    : actionRepository.findAllByOrderByPerformedAtDesc();
        }

        var stream = actions.stream().map(actionMapper::toResponse);
        if (limit != null) {
            stream = stream.limit(limit);
        }
        return stream.toList();
    }

    public ActionResponse getAction(Long actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));
        return actionMapper.toResponse(action);
    }
}
