package com.greenhouse.action;

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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private CropRepository cropRepository;

    private final ActionMapper actionMapper = new ActionMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ActionService service() {
        return new ActionService(actionRepository, cropRepository, actionMapper, fixedClock);
    }

    @Test
    void recordAction_validInput_persistsWithDefaults() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> {
            Action action = invocation.getArgument(0);
            action.setId(10L);
            return action;
        });

        ActionResponse response = service().recordAction(1L, ActionType.WATER, "Watered after soil dried out.",
                100.0, "ml", null, null);

        assertThat(response.type()).isEqualTo(ActionType.WATER);
        assertThat(response.quantity()).isEqualTo(100.0);
        assertThat(response.unit()).isEqualTo("ml");
        assertThat(response.performedAt()).isEqualTo(FIXED_NOW);
        assertThat(response.performedBy()).isEqualTo(ActionPerformedBy.HUMAN);
    }

    @Test
    void recordAction_explicitPerformedAtAndPerformedBy_arePreserved() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant historical = Instant.parse("2026-08-01T08:00:00Z");
        ActionResponse response = service().recordAction(1L, ActionType.PRUNE, "Removed a runner.",
                null, null, historical, ActionPerformedBy.AGENT);

        assertThat(response.performedAt()).isEqualTo(historical);
        assertThat(response.performedBy()).isEqualTo(ActionPerformedBy.AGENT);
    }

    @Test
    void recordAction_missingCropId_throwsValidationException() {
        assertThatThrownBy(() -> service().recordAction(null, ActionType.WATER, null, null, null, null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordAction_unknownCrop_throwsNotFound() {
        when(cropRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service().recordAction(99L, ActionType.WATER, null, null, null, null, null))
                .isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void recordAction_missingType_throwsValidationException() {
        when(cropRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service().recordAction(1L, null, null, null, null, null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordAction_quantityWithoutUnit_throwsValidationException() {
        when(cropRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service().recordAction(1L, ActionType.WATER, null, 100.0, null, null, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recordAction_typeWithoutQuantity_isAllowed() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionResponse response = service().recordAction(1L, ActionType.POLLINATE,
                "Hand-pollinated three open flowers.", null, null, null, null);

        assertThat(response.quantity()).isNull();
        assertThat(response.unit()).isNull();
    }

    @Test
    void listActions_scopedToCrop_returnsCropActions() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        Action action = newAction(1L, ActionType.WATER);
        when(actionRepository.findAllByCropIdOrderByPerformedAtDesc(1L)).thenReturn(List.of(action));

        List<ActionResponse> actions = service().listActions(1L, null, null);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).cropId()).isEqualTo(1L);
    }

    @Test
    void listActions_withLimit_truncatesResults() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        when(actionRepository.findAllByCropIdOrderByPerformedAtDesc(1L)).thenReturn(List.of(
                newAction(1L, ActionType.WATER), newAction(1L, ActionType.FEED), newAction(1L, ActionType.PRUNE)
        ));

        List<ActionResponse> actions = service().listActions(1L, 2, null);

        assertThat(actions).hasSize(2);
    }

    @Test
    void listActions_withSince_usesFilteredQuery() {
        when(cropRepository.existsById(1L)).thenReturn(true);
        Instant since = Instant.parse("2026-08-10T00:00:00Z");
        when(actionRepository.findAllByCropIdAndPerformedAtAfterOrderByPerformedAtDesc(1L, since))
                .thenReturn(List.of(newAction(1L, ActionType.WATER)));

        List<ActionResponse> actions = service().listActions(1L, null, since);

        assertThat(actions).hasSize(1);
    }

    @Test
    void listActions_noCropId_returnsAllActions() {
        when(actionRepository.findAllByOrderByPerformedAtDesc()).thenReturn(List.of(
                newAction(1L, ActionType.WATER), newAction(2L, ActionType.FEED)
        ));

        List<ActionResponse> actions = service().listActions(null, null, null);

        assertThat(actions).hasSize(2);
    }

    @Test
    void listActions_unknownCrop_throwsNotFound() {
        when(cropRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service().listActions(404L, null, null))
                .isInstanceOf(CropNotFoundException.class);
    }

    @Test
    void listActions_nonPositiveLimit_throwsValidationException() {
        assertThatThrownBy(() -> service().listActions(1L, 0, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void getAction_existingAction_returnsIt() {
        Action action = newAction(1L, ActionType.WATER);
        action.setId(5L);
        when(actionRepository.findById(5L)).thenReturn(Optional.of(action));

        ActionResponse response = service().getAction(5L);

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void getAction_unknownAction_throwsNotFound() {
        when(actionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getAction(404L))
                .isInstanceOf(ActionNotFoundException.class)
                .hasMessageContaining("404");
    }

    private static Action newAction(Long cropId, ActionType type) {
        Action action = new Action();
        action.setCropId(cropId);
        action.setType(type);
        action.setPerformedAt(FIXED_NOW);
        action.setPerformedBy(ActionPerformedBy.HUMAN);
        action.setCreatedAt(FIXED_NOW);
        return action;
    }
}
