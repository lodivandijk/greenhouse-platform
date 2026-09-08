package com.greenhouse.briefing.summary;

// The briefing's prose, and an honest label of where it came from.
//
// The source is recorded because a reader is entitled to know whether a
// sentence was assembled from the numbers by code or written by a language
// model - those warrant different levels of trust, and the structured evidence
// sits beside it either way (ADR-029).
public record BriefingSummary(
        String greenhouseSummary,
        Source source,
        // Null unless a model wrote it.
        String model,
        // Null unless the model was tried and failed; recorded so a silently
        // degraded briefing is visible rather than merely plausible.
        String fallbackReason
) {

    public enum Source {
        LANGUAGE_MODEL,
        DETERMINISTIC
    }

    public static BriefingSummary deterministic(String text) {
        return new BriefingSummary(text, Source.DETERMINISTIC, null, null);
    }

    public static BriefingSummary deterministicAfterFailure(String text, String reason) {
        return new BriefingSummary(text, Source.DETERMINISTIC, null, reason);
    }

    public static BriefingSummary fromModel(String text, String model) {
        return new BriefingSummary(text, Source.LANGUAGE_MODEL, model, null);
    }

    public String attribution() {
        return source == Source.LANGUAGE_MODEL
                ? "Written by " + model + " from the structured readings below, which remain the record."
                : "Assembled from the readings below by the platform itself.";
    }
}
