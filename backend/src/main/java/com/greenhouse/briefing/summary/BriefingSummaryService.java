package com.greenhouse.briefing.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

// Produces the briefing's prose, preferring a language model and falling back
// to the deterministic text.
//
// The fallback is not a nicety. A briefing is the thing that tells someone a
// plant is dying; it must be produced on a morning when the API is down, the
// key has expired, or the account is out of credit. So the deterministic
// sentences are always computed first, the model is offered the same facts, and
// any failure at all leaves the deterministic version in place with the reason
// recorded (ADR-029).
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
            LOGGER.info(
                    "Briefing summaries will be written by the platform itself. To use a language model, "
                            + "set greenhouse.daily-briefing.summary.llm-enabled=true and provide "
                            + "ANTHROPIC_API_KEY.");
        } else {
            LOGGER.info("Briefing summaries will be written by {}.", properties.model());
        }
    }

    public BriefingSummary summarise(String deterministicSummary, List<String> cropSummaries, String factSheet) {
        String deterministic = deterministicSummary;

        ClaudeSummaryComposer composer = composerProvider.getIfAvailable();
        if (composer == null) {
            return BriefingSummary.deterministic(deterministic);
        }

        try {
            return BriefingSummary.fromModel(composer.compose(factSheet), properties.model());
        } catch (Exception e) {
            // Every failure mode lands here on purpose - no key, no credit, no
            // network, a refusal, a timeout. None of them may stop the briefing.
            LOGGER.warn("Briefing summary fell back to deterministic text: {}", e.toString());
            return BriefingSummary.deterministicAfterFailure(
                    deterministic, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // Everything the model is allowed to know, in the order a person would want
    // it. Built from the same snapshot the structured briefing is built from,
    // so any sentence in the summary can be traced to a line here.
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
            cropLines.forEach(line -> sheet.append("- ").append(line).append("\n"));
        }

        sheet.append("\nACTIVE WARNINGS\n");
        if (warningLines.isEmpty()) {
            sheet.append("None.\n");
        } else {
            warningLines.forEach(line -> sheet.append("- ").append(line).append("\n"));
        }

        sheet.append("\nOPEN CARE LOOPS (things waiting on the reader)\n");
        if (loopLines.isEmpty()) {
            sheet.append("None.\n");
        } else {
            loopLines.forEach(line -> sheet.append("- ").append(line).append("\n"));
        }

        sheet.append("\nDATA GAPS (measurements that could not be taken)\n");
        if (gapLines.isEmpty()) {
            sheet.append("None - every configured sensor reported.\n");
        } else {
            gapLines.forEach(line -> sheet.append("- ").append(line).append("\n"));
        }

        return sheet.toString();
    }
}
