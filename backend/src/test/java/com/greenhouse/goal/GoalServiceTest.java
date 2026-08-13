package com.greenhouse.goal;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.crop.CropNotFoundException;
import com.greenhouse.crop.CropRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private CropRepository cropRepository;

    private final GoalMapper goalMapper = new GoalMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private GoalService service() {
        return new GoalService(goalRepository, cropRepository, goalMapper, fixedClock);
    }

    @Test
    void createGoal_validKnownType_persistsAsActive() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> {
            Goal goal = invocation.getArgument(0);
            goal.setId(7L);
            return goal;
        });

        GoalResponse response = service().createGoal(
                1L, GoalType.MAXIMISE_FOLIAGE, null,
                "I want as much usable foliage as possible for as long as possible.", null
        );

        assertThat(response.goalType()).isEqualTo(GoalType.MAXIMISE_FOLIAGE);
        assertThat(response.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(response.sourceInstruction())
                .isEqualTo("I want as much usable foliage as possible for as long as possible.");
    }

    @Test
    void createGoal_otherTypeWithDescription_persists() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponse response = service().createGoal(1L, GoalType.OTHER, "Encourage early flowering", null, 1);

        assertThat(response.goalType()).isEqualTo(GoalType.OTHER);
        assertThat(response.description()).isEqualTo("Encourage early flowering");
    }

    @Test
    void createGoal_otherTypeWithoutDescription_throwsValidationException() {
        when(cropRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service().createGoal(1L, GoalType.OTHER, null, null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void createGoal_unknownCrop_throwsNotFound() {
        when(cropRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service().createGoal(99L, GoalType.MAXIMISE_FOLIAGE, null, null, null))
                .isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void createGoal_missingGoalType_throwsValidationException() {
        when(cropRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service().createGoal(1L, null, null, null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void deleteGoal_existingGoal_deletesAndReturnsIt() {
        Goal goal = new Goal();
        goal.setId(3L);
        goal.setCropId(1L);
        goal.setGoalType(GoalType.MAXIMISE_FOLIAGE);
        goal.setStatus(GoalStatus.ACTIVE);

        when(goalRepository.findById(3L)).thenReturn(Optional.of(goal));

        GoalResponse response = service().deleteGoal(3L);

        assertThat(response.id()).isEqualTo(3L);
        verify(goalRepository).delete(goal);
    }

    @Test
    void deleteGoal_unknownGoal_throwsNotFound() {
        when(goalRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteGoal(404L))
                .isInstanceOf(GoalNotFoundException.class)
                .hasMessageContaining("404");
    }
}
