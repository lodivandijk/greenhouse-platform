package com.greenhouse.briefing.summary;

// The briefing's prose, or an honest statement that there isn't any.
//
// There is one writer. The platform previously assembled its own sentences as a
// fallback, which meant two prose paths to keep honest and a fact sheet made of
// prose being fed to a model to be rewritten as prose. Now the model writes it
// or nobody does, and the structured briefing - which never depended on either -
// carries on regardless (ADR-030).
public record BriefingSummary(
        // Null when no summary could be produced.
        String text,
        String model,
        // Null when a summary was produced; otherwise why there is none.
        String unavailableReason
) {

    public static BriefingSummary written(String text, String model) {
        return new BriefingSummary(text, model, null);
    }

    public static BriefingSummary unavailable(String reason) {
        return new BriefingSummary(null, null, reason);
    }

    public boolean isPresent() {
        return text != null && !text.isBlank();
    }

    public String attribution() {
        return isPresent()
                ? "Written by " + model + " from the readings below, which remain the record."
                : "No summary this morning: " + unavailableReason
                        + " The readings below are unaffected.";
    }
}
