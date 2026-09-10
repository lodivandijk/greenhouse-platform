package com.greenhouse.briefing.summary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// There is one summary writer now. What must hold is that its absence or
// failure costs the paragraph and nothing else, and that the absence is stated
// rather than looking like a morning with nothing to say (ADR-030).
class BriefingSummaryTest {

    private final BriefingSummaryProperties properties =
            new BriefingSummaryProperties(true, "claude-opus-5", 2000, Duration.ofSeconds(60));

    @SuppressWarnings("unchecked")
    private BriefingSummaryService serviceWith(ClaudeSummaryComposer composer) {
        ObjectProvider<ClaudeSummaryComposer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(composer);
        return new BriefingSummaryService(provider, properties);
    }

    @Test
    void aWrittenSummaryIsAttributedToTheModel() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString())).thenReturn(new ClaudeSummaryComposer.ComposedSummary(
                "Thyme needs a look.", "The thyme is the one worth a look."));

        BriefingSummary summary = serviceWith(composer).summarise("fact sheet");

        assertThat(summary.isPresent()).isTrue();
        assertThat(summary.text()).contains("thyme");
        assertThat(summary.headline()).isEqualTo("Thyme needs a look.");
        assertThat(summary.model()).isEqualTo("claude-opus-5");
        assertThat(summary.attribution()).contains("claude-opus-5").contains("remain the record");
        assertThat(summary.unavailableReason()).isNull();
    }

    @Test
    void withNoWriterConfiguredThereIsNoSummaryAndItSaysSo() {
        BriefingSummary summary = serviceWith(null).summarise("fact sheet");

        assertThat(summary.isPresent()).isFalse();
        assertThat(summary.unavailableReason()).contains("no summary writer is configured");
        assertThat(summary.attribution()).contains("The readings below are unaffected");
    }

    // No key, no credit, no network, a refusal, a timeout - all the same to the
    // briefing, which must still be produced.
    @Test
    void anApiFailureLeavesTheBriefingIntactAndRecordsWhy() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString())).thenThrow(new RuntimeException("connection refused"));

        BriefingSummary summary = serviceWith(composer).summarise("fact sheet");

        assertThat(summary.isPresent()).isFalse();
        assertThat(summary.unavailableReason()).contains("could not be written");
        assertThat(summary.unavailableReason()).contains("RuntimeException");
    }

    @Test
    void aRefusalIsAFailureNotAnEmptySummary() {
        ClaudeSummaryComposer composer = mock(ClaudeSummaryComposer.class);
        when(composer.compose(anyString()))
                .thenThrow(new IllegalStateException("The model returned no text (stop reason: refusal)."));

        assertThat(serviceWith(composer).summarise("fact sheet").isPresent()).isFalse();
    }

    // The fact sheet is the model's only permitted source, so absences must be
    // present in it explicitly - a missing section reads as "nothing to report".
    @Test
    void theFactSheetStatesAbsencesExplicitly() {
        String sheet = serviceWith(null).buildFactSheet(
                "Status NORMAL.", List.of("- Mint (crop 10): moisture index 56, dry line 50."),
                List.of(), List.of(), List.of());

        assertThat(sheet).contains("ACTIVE WARNINGS").contains("None.");
        assertThat(sheet).contains("OPEN CARE LOOPS");
        assertThat(sheet).contains("DATA GAPS").contains("every configured sensor reported");
    }

    @Test
    void theFactSheetCarriesWarningsLoopsAndGapsWhenTheyExist() {
        String sheet = serviceWith(null).buildFactSheet(
                "Status NORMAL.",
                List.of("- Thyme (crop 9): moisture index 100."),
                List.of("Thyme (crop 9): CROP_SOIL_MOISTURE_HIGH - too wet"),
                List.of("Loop 3 on CROP 9 - waterlogged, status AWAITING_HUMAN_REVIEW, next: review"),
                List.of("kind=NO_SENSOR_ASSIGNED, cropId=13"));

        assertThat(sheet).contains("CROP_SOIL_MOISTURE_HIGH").contains("Loop 3")
                .contains("NO_SENSOR_ASSIGNED");
    }
}
