package com.greenhouse.briefing.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

// Produces the briefing's prose, or says plainly that it could not.
//
// A failure costs an opening paragraph, not the briefing: the greenhouse
// conditions, per-crop trends, warnings, open loops and data gaps are all
// computed before this runs and are emitted whatever happens here. That is why
// the deterministic writer could be removed rather than kept as a second path
// to maintain - it was only ever protecting the paragraph (ADR-030).
@Service
public class BriefingSummaryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BriefingSummaryService.class);

    private final ObjectProvider<ClaudeSummaryComposer> composerProvider;
    private final BriefingSummaryProperties properties;

    public BriefingSummaryService(
            ObjectProvider<ClaudeSummaryComposer> composerProvider,
            BriefingSummaryProperties properties
    ) {
        this.composerProvider = composerProvider;
        this.properties = properties;
    }

    // Announced at startup because the failure this catches is silent: a
    // property bound at the wrong prefix leaves the feature switched on in the
    // environment and off in the application, with nothing in the logs and a
    // briefing that looks fine.
    @jakarta.annotation.PostConstruct
    void announceConfiguredSource() {
        if (composerProvider.getIfAvailable() == null) {
            LOGGER.warn(
                    "No briefing summary writer is configured - briefings will carry their readings but "
                            + "no opening paragraph. Set greenhouse.daily-briefing.summary.llm-enabled=true "
                            + "and provide ANTHROPIC_API_KEY.");
        } else {
            LOGGER.info("Briefing summaries will be written by {}.", properties.model());
        }
    }

    public BriefingSummary summarise(String factSheet, com.greenhouse.briefing.BriefingEdition edition) {
        ClaudeSummaryComposer composer = composerProvider.getIfAvailable();
        if (composer == null) {
            return BriefingSummary.unavailable("no summary writer is configured.");
        }

        try {
            ClaudeSummaryComposer.ComposedSummary composed = composer.compose(factSheet, edition);
            return BriefingSummary.written(composed.text(), composed.headline(), properties.model());
        } catch (Exception e) {
            // Every failure mode lands here on purpose - no key, no credit, no
            // network, a refusal, a timeout. None of them may stop the briefing.
            LOGGER.warn("Briefing summary could not be written: {}", e.toString());
            return BriefingSummary.unavailable(
                    "the summary could not be written (" + e.getClass().getSimpleName() + ").");
        }
    }

    // The model's only permitted source, built from the structured briefing
    // rather than from sentences. Feeding it prose to rewrite would put a second
    // author between the data and the reader.
    public String buildFactSheet(
            String greenhouseLine,
            List<String> cropLines,
            List<String> warningLines,
            List<String> loopLines,
            List<String> gapLines
    ) {
        StringBuilder sheet = new StringBuilder();

        sheet.append("GREENHOUSE\n").append(greenhouseLine).append("\n\n");

        sheet.append("CROPS\n");
        if (cropLines.isEmpty()) {
            sheet.append("No crops are being tracked.\n");
        } else {
            cropLines.forEach(line -> sheet.append(line).append("\n"));
        }

        sheet.append("\nACTIVE WARNINGS\n");
        appendOrNone(sheet, warningLines, "None.");

        sheet.append("\nOPEN CARE LOOPS (things waiting on the reader)\n");
        appendOrNone(sheet, loopLines, "None.");

        sheet.append("\nDATA GAPS (measurements that could not be taken)\n");
        appendOrNone(sheet, gapLines, "None - every configured sensor reported.");

        return sheet.toString();
    }

    private static void appendOrNone(StringBuilder sheet, List<String> lines, String noneText) {
        if (lines.isEmpty()) {
            sheet.append(noneText).append("\n");
        } else {
            lines.forEach(line -> sheet.append("- ").append(line).append("\n"));
        }
    }
}
