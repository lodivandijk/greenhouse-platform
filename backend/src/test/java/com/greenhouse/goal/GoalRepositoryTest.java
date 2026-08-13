package com.greenhouse.goal;

import com.greenhouse.crop.Crop;
import com.greenhouse.crop.CropRepository;
import com.greenhouse.crop.CropStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoalRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private GoalRepository goalRepository;

    private Long saveCrop() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        Crop crop = new Crop();
        crop.setSpecies("basil-" + UUID.randomUUID());
        crop.setLocationId("planter-01");
        crop.setPlantedAt(now);
        crop.setStatus(CropStatus.PRODUCTIVE);
        crop.setCreatedAt(now);
        crop.setUpdatedAt(now);
        return cropRepository.save(crop).getId();
    }

    private static Goal newGoal(Long cropId, GoalStatus status, Instant createdAt) {
        Goal goal = new Goal();
        goal.setCropId(cropId);
        goal.setGoalType(GoalType.MAXIMISE_FOLIAGE);
        goal.setDescription("Maximise usable foliage.");
        goal.setStatus(status);
        goal.setSourceInstruction("I want as much usable foliage as possible for as long as possible.");
        goal.setMetadata(Map.of());
        goal.setCreatedAt(createdAt);
        goal.setUpdatedAt(createdAt);
        return goal;
    }

    @Test
    void findAllByCropId_returnsInCreationOrder() {
        Long cropId = saveCrop();

        goalRepository.save(newGoal(cropId, GoalStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z")));
        goalRepository.save(newGoal(cropId, GoalStatus.ACTIVE, Instant.parse("2026-08-05T12:00:00Z")));

        List<Goal> goals = goalRepository.findAllByCropIdOrderByCreatedAtAsc(cropId);

        assertThat(goals).hasSize(2);
        assertThat(goals.get(0).getCreatedAt()).isBefore(goals.get(1).getCreatedAt());
    }

    @Test
    void findAllByCropIdAndStatus_filtersCorrectly() {
        Long cropId = saveCrop();

        goalRepository.save(newGoal(cropId, GoalStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z")));
        Goal cancelled = newGoal(cropId, GoalStatus.CANCELLED, Instant.parse("2026-08-11T12:00:00Z"));
        goalRepository.save(cancelled);

        List<Goal> active = goalRepository.findAllByCropIdAndStatusOrderByCreatedAtAsc(cropId, GoalStatus.ACTIVE);

        assertThat(active).extracting(Goal::getStatus).containsOnly(GoalStatus.ACTIVE);
    }

    @Test
    void sourceInstruction_roundTripsCorrectly() {
        Long cropId = saveCrop();
        Instant now = Instant.parse("2026-08-13T12:00:00Z");

        Long id = goalRepository.save(newGoal(cropId, GoalStatus.ACTIVE, now)).getId();

        Goal reloaded = goalRepository.findById(id).orElseThrow();
        assertThat(reloaded.getSourceInstruction())
                .isEqualTo("I want as much usable foliage as possible for as long as possible.");
        assertThat(reloaded.getGoalType()).isEqualTo(GoalType.MAXIMISE_FOLIAGE);
    }
}
