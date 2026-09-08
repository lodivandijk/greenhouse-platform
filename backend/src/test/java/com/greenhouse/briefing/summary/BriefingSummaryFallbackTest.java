package com.greenhouse.briefing.summary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A briefing is the thing that tells someone a plant is dying. It must be
// produced on a morning when the API is down, the key has expired or the
// account is out of credit - so every model failure lands on the deterministic
// text, and the reason is recorded rather than hidden (ADR-029).
class BriefingSummaryFallbackTest {

    private final BriefingSummaryProperties properties =
            new BriefingSummaryProperties(true, "claude-opus-5", 2000, Duration.ofSeconds(60));

    private static final String DETERMINISTIC = "Nothing is asking for your attention this morning.";

    @SuppressWarnings("unchecked")
    private BriefingSummaryService serviceWith(ClaudeSummaryComposer composer) {
        ObjectProvider<ClaudeSummaryComposer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(composer);
        return new BriefingSummaryService(provider, properties);
    }

    @Test
    void withNoComposerConfiguredTheDeterministicTextIsUsed() {
        BriefingSummary summary = serviceWith(null)
                .summarise(DETERMINISTIC, List.of(), "fact sheet");

        assertThat(summary.source()).isEqualTo(BriefingSummary.Source.DETERMINISTIC);
        assertThat(summary.greenhouseSummary()).isEqualTo(DETERMINISTIC);
        assertThat(summary.model()).isNull();
        // Not a failure - it was never switched on.
        assertThat(summary.fallbackReason()).isNull();
    }

    @Test
    void aSuccessfulCompositionIsAttributedToTheModel() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString())).thenReturn("The mint has been drying for six days.");

        BriefingSummary summary = serviceWith(composer)
                .summarise(DETERMINISTIC, List.of(), "fact sheet");

        assertThat(summary.source()).isEqualTo(BriefingSummary.Source.LANGUAGE_MODEL);
        assertThat(summary.greenhouseSummary()).contains("drying for six days");
        assertThat(summary.model()).isEqualTo("claude-opus-5");
        assertThat(summary.attribution()).contains("claude-opus-5");
        assertThat(summary.attribution()).contains("which remain the record");
    }

    @Test
    void anApiFailureFallsBackAndRecordsWhy() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        BriefingSummary summary = serviceWith(composer)
                .summarise(DETERMINISTIC, List.of(), "fact sheet");

        assertThat(summary.source()).isEqualTo(BriefingSummary.Source.DETERMINISTIC);
        assertThat(summary.greenhouseSummary()).isEqualTo(DETERMINISTIC);
        // A silently degraded briefing would look identical to a healthy one.
        assertThat(summary.fallbackReason()).contains("connection refused");
    }

    @Test
    void anEmptyOrRefusedResponseIsTreatedAsAFailure() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString()))
                .thenThrow(new IllegalStateException("The model returned no text (stop reason: refusal)."));

        BriefingSummary summary = serviceWith(composer)
                .summarise(DETERMINISTIC, List.of(), "fact sheet");

        assertThat(summary.source()).isEqualTo(BriefingSummary.Source.DETERMINISTIC);
        assertThat(summary.fallbackReason()).contains("refusal");
    }

    // The fact sheet is the model's only permitted source, so what goes into it
    // matters as much as the prompt.
    @Test
    void theFactSheetStatesAbsencesExplicitlyRatherThanOmittingThem() {
        String sheet = serviceWith(null).buildFactSheet(
                "The greenhouse is 21.0C.", List.of("Mint is at index 44."),
                List.of(), List.of(), List.of());

        assertThat(sheet).contains("ACTIVE WARNINGS").contains("None.");
        assertThat(sheet).contains("OPEN CARE LOOPS");
        assertThat(sheet).contains("DATA GAPS")
                .contains("every configured sensor reported");
    }

    @Test
    void theFactSheetCarriesWarningsLoopsAndGapsWhenTheyExist() {
        String sheet = serviceWith(null).buildFactSheet(
                "The greenhouse is 24.0C.",
                List.of("Thyme is at index 100."),
                List.of("Thyme (crop 9): CROP_SOIL_MOISTURE_HIGH - too wet"),
                List.of("Loop 3 on CROP 9 - waterlogged, status AWAITING_HUMAN_REVIEW, next: review"),
                List.of("kind=NO_SENSOR_ASSIGNED, cropId=13"));

        assertThat(sheet).contains("CROP_SOIL_MOISTURE_HIGH");
        assertThat(sheet).contains("Loop 3");
        assertThat(sheet).contains("NO_SENSOR_ASSIGNED");
    }
}
