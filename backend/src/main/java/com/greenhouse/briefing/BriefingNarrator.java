package com.greenhouse.briefing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Writes the briefing's prose.
//
// Deterministic by construction: no LLM is involved, so the same facts always
// produce the same words and every sentence can be traced to a number in the
// same snapshot (ADR-023, ADR-029). It states what was measured and what
// changed; it does not diagnose, and it does not claim to know WHY something
// moved. "Rose sharply, consistent with watering" is as far as it goes -
// asserting that someone watered would be inventing a fact.
@Component
public class BriefingNarrator {

    // --- greenhouse headline ---------------------------------------------

    public String greenhouseSummary(
            int cropsNeedingAttention,
            int openLoops,
            int dataGaps,
            Double temperatureCelsius,
            Double humidityPercent,
            String freshness,
            List<String> attentionCropNames
    ) {
        List<String> sentences = new ArrayList<>();

        if (cropsNeedingAttention == 0 && openLoops == 0) {
            sentences.add("Nothing is asking for your attention this morning.");
        } else if (cropsNeedingAttention > 0) {
            sentences.add(String.format(Locale.ROOT,
                    "%s %s flagged this morning: %s.",
                    cropsNeedingAttention == 1 ? "One crop is" : cropsNeedingAttention + " crops are",
                    cropsNeedingAttention == 1 ? "" : "",
                    String.join(", ", attentionCropNames)).replace("  ", " "));
        }

        if (openLoops > 0) {
            sentences.add(String.format(Locale.ROOT,
                    "%s still open and waiting on you.",
                    openLoops == 1 ? "One care loop is" : openLoops + " care loops are"));
        }

        if (temperatureCelsius != null && humidityPercent != null) {
            sentences.add(String.format(Locale.ROOT,
                    "The greenhouse is %.1f°C at %.0f%% humidity%s.",
                    temperatureCelsius, humidityPercent,
                    "CURRENT".equals(freshness) ? "" : " (reading is " + freshness.toLowerCase() + ")"));
        } else {
            sentences.add("No current environmental reading is available.");
        }

        if (dataGaps > 0) {
            sentences.add(String.format(Locale.ROOT,
                    "%s in the data - see the gaps section; anything not measured is not being watched.",
                    dataGaps == 1 ? "There is one gap" : "There are " + dataGaps + " gaps"));
        }

        return String.join(" ", sentences);
    }

    // --- per crop ---------------------------------------------------------

    public String cropSummary(CropNarrativeInput input) {
        List<String> sentences = new ArrayList<>();

        sentences.add(openingSentence(input));

        String trendSentence = trendSentence(input);
        if (trendSentence != null) {
            sentences.add(trendSentence);
        }

        if (!input.activeAssessmentDescriptions().isEmpty()) {
            sentences.add("Flagged: " + String.join("; ", input.activeAssessmentDescriptions()) + ".");
        }

        String actionSentence = actionSentence(input);
        if (actionSentence != null) {
            sentences.add(actionSentence);
        }

        if (input.nextRequiredAction() != null && !input.nextRequiredAction().isBlank()) {
            sentences.add("Waiting on you: " + input.nextRequiredAction());
        }

        return String.join(" ", sentences);
    }

    private String openingSentence(CropNarrativeInput input) {
        String name = input.species() + " (crop " + input.cropId() + ")";

        if (input.manuallyMonitored()) {
            return name + " is monitored by hand, so its soil condition is not measured and is not "
                    + "being watched - judge it by looking at it.";
        }
        if (input.currentIndex() == null) {
            return name + " has no usable soil reading right now"
                    + (input.soilUnavailableReason() == null
                            ? "." : ", because " + input.soilUnavailableReason() + ".");
        }

        return String.format(Locale.ROOT,
                "%s is at moisture index %.0f against a dry line of %.0f%s.",
                name, input.currentIndex(), input.dryThresholdIndex(),
                input.wetThresholdIndex() == null
                        ? " and no wet ceiling"
                        : String.format(Locale.ROOT, " and a wet ceiling of %.0f",
                                input.wetThresholdIndex()));
    }

    private String trendSentence(CropNarrativeInput input) {
        CropSoilTrend trend = input.trend();
        if (trend == null || !trend.isKnown()) {
            if (!input.manuallyMonitored() && input.currentIndex() != null) {
                return "There is not yet enough history to show a trend.";
            }
            return null;
        }

        StringBuilder sentence = new StringBuilder();
        switch (trend.direction()) {
            case FALLING -> sentence.append(String.format(Locale.ROOT,
                    "It has been drying for %d days, losing about %.1f index points a day (%.0f down to %.0f)",
                    trend.daysObserved(), Math.abs(trend.changePerDay()),
                    trend.earliestIndex(), trend.latestIndex()));
            case RISING -> sentence.append(String.format(Locale.ROOT,
                    "It has been getting wetter over %d days, gaining about %.1f index points a day "
                            + "(%.0f up to %.0f)",
                    trend.daysObserved(), trend.changePerDay(),
                    trend.earliestIndex(), trend.latestIndex()));
            case STEADY -> sentence.append(String.format(Locale.ROOT,
                    "It has held steady for %d days, around %.0f", trend.daysObserved(),
                    trend.latestIndex()));
            default -> {
                return null;
            }
        }

        if (trend.sharpRiseObserved()) {
            // Consistent with, not caused by: the platform did not see anyone
            // water, it saw the number jump.
            sentence.append(", with a sharp rise at some point in that window consistent with watering");
        }
        sentence.append(".");

        if (trend.daysUntilDryThreshold() != null) {
            sentence.append(String.format(Locale.ROOT,
                    " At that rate it reaches its dry line in roughly %s.",
                    humaniseDays(trend.daysUntilDryThreshold())));
        }

        return sentence.toString();
    }

    private String actionSentence(CropNarrativeInput input) {
        if (input.recentActionDescriptions().isEmpty()) {
            if (input.daysSinceLastAction() != null) {
                return String.format(Locale.ROOT,
                        "Nothing has been recorded for it in the last %s; the last was %s ago.",
                        input.windowDays() == 1 ? "day" : input.windowDays() + " days",
                        input.daysSinceLastAction() == 1 ? "a day" : input.daysSinceLastAction() + " days");
            }
            return "Nothing has been recorded for it, so there is no history of work on this crop.";
        }
        return "Recorded recently: " + String.join(", ", input.recentActionDescriptions()) + ".";
    }

    private String humaniseDays(double days) {
        if (days < 1.0) {
            return "under a day";
        }
        if (days < 1.5) {
            return "a day";
        }
        return String.format(Locale.ROOT, "%.0f days", days);
    }
}
