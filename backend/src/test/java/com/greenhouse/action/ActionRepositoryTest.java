package com.greenhouse.action;

import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActionRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private ActionRepository actionRepository;

    private Long saveCrop() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        Crop crop = new Crop();
        crop.setSpecies("strawberry-" + UUID.randomUUID());
        crop.setLocationId("pot-2");
        crop.setPlantedAt(now);
        crop.setStatus(CropStatus.PRODUCTIVE);
        crop.setCreatedAt(now);
        crop.setUpdatedAt(now);
        return cropRepository.save(crop).getId();
    }

    private static Action newAction(Long cropId, ActionType type, Instant performedAt) {
        Action action = new Action();
        action.setCropId(cropId);
        action.setType(type);
        action.setPerformedAt(performedAt);
        action.setPerformedBy(ActionPerformedBy.HUMAN);
        action.setCreatedAt(performedAt);
        return action;
    }

    @Test
    void findAllByCropId_returnsInReverseChronologicalOrder() {
        Long cropId = saveCrop();

        actionRepository.save(newAction(cropId, ActionType.WATER, Instant.parse("2026-08-10T08:00:00Z")));
        actionRepository.save(newAction(cropId, ActionType.FEED, Instant.parse("2026-08-12T08:00:00Z")));
        actionRepository.save(newAction(cropId, ActionType.PRUNE, Instant.parse("2026-08-05T08:00:00Z")));

        List<Action> actions = actionRepository.findAllByCropIdOrderByPerformedAtDesc(cropId);

        assertThat(actions).extracting(Action::getType)
                .containsExactly(ActionType.FEED, ActionType.WATER, ActionType.PRUNE);
    }

    @Test
    void findAllByCropIdAndPerformedAtAfter_filtersCorrectly() {
        Long cropId = saveCrop();
        Instant since = Instant.parse("2026-08-08T00:00:00Z");

        actionRepository.save(newAction(cropId, ActionType.WATER, Instant.parse("2026-08-10T08:00:00Z")));
        actionRepository.save(newAction(cropId, ActionType.PRUNE, Instant.parse("2026-08-05T08:00:00Z")));

        List<Action> actions = actionRepository.findAllByCropIdAndPerformedAtAfterOrderByPerformedAtDesc(cropId, since);

        assertThat(actions).extracting(Action::getType).containsExactly(ActionType.WATER);
    }

    @Test
    void quantityUnitAndDescription_roundTripCorrectly() {
        Long cropId = saveCrop();
        Action action = newAction(cropId, ActionType.WATER, Instant.parse("2026-08-14T08:00:00Z"));
        action.setQuantity(100.0);
        action.setUnit("ml");
        action.setDescription("Watered after soil dried out.");

        Long id = actionRepository.save(action).getId();

        Action reloaded = actionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(100.0);
        assertThat(reloaded.getUnit()).isEqualTo("ml");
        assertThat(reloaded.getDescription()).isEqualTo("Watered after soil dried out.");
        assertThat(reloaded.getPerformedBy()).isEqualTo(ActionPerformedBy.HUMAN);
    }
}
