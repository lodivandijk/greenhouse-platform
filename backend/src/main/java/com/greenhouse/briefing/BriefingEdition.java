package com.greenhouse.briefing;

// Which of the day's briefings this is.
//
// An edition is not just a time - it is a purpose. The morning briefing is read
// before leaving the house and answers "is there anything I should know before
// I go". The evening one is read on getting home and answers "what can I
// usefully do tonight". They are given different prompts for that reason
// (ADR-033).
//
// Adding a third is deliberately a code change rather than another entry in a
// config list, so that a new edition is a decision about what it is FOR.
public enum BriefingEdition {

    MORNING("morning"),
    EVENING("evening");

    private final String label;

    BriefingEdition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
