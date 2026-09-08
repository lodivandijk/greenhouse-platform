# ADR-029: Briefing Trends, and a Language Model Composing the Summary

**Status:** Accepted
**Date:** 2026-09-08
**Supersedes:** the "no LLM writes notifications" clause of ADR-023, narrowly — see below.

## Context

The daily briefing described the greenhouse's current state and nothing else. Every crop's soil reading, every active assessment, every open loop — all accurate, all a snapshot of one moment.

The mint made the cost of that obvious. Its moisture index fell 78 → 74 → 67 → 62 → 50 → 46 → 44 over six days. Every one of those briefings was correct and none of them was useful, because no single day looked alarming. The information needed to see it coming existed in the database the whole time; the briefing simply never looked backwards.

Two things were missing, and they are different problems:

1. **Trends** — a derived fact the platform never computed.
2. **A summary** — the briefing was a set of tables, which is a poor format for something read once, on a phone, before deciding whether to go outside.

## Decision

### Trends are computed as derived facts

`CropSoilTrendService` reduces a week of one-minute samples to daily means (a native aggregate — ~10,000 rows per probe is not something to pull into memory), converts them to the moisture index, and reports direction, rate in index points per day, and whether a sharp day-over-day rise occurred.

Daily means rather than raw samples so a single noisy reading cannot invent a trend, and a day with fewer than 60 samples is treated as a data gap rather than a measurement.

A **projection** — "reaches its dry line in about 3 days" — is offered only while a crop is genuinely drying and has not yet crossed its threshold, and only within a 21-day horizon. It is labelled an extrapolation wherever it appears, because it assumes a rate that will not hold: soil dries more slowly as it gets drier, weather changes, and watering resets it. It is worth showing anyway, since "sooner than you think" is the useful signal.

Trends raise no assessments and open no care loops. They are evidence for a human, not a new input to the engine.

### A language model composes the summary, and ADR-023 is narrowed

ADR-023 said content is deterministic and no LLM writes notifications. That decision was correct **for the delivery path** — a renderer invoked on every send and every retry, where a model call is non-idempotent, billed per attempt, and impossible to test for the honesty properties that ADR-023 exists to guarantee.

Briefing composition is a different shape, and the distinction is what justifies the change:

- It runs **once a day**, not once per delivery attempt.
- Its output is written into an **immutable, versioned snapshot** — captured once, auditable forever, never regenerated differently for the same day.
- It **decides nothing**. Assessments, thresholds, care loops and outcomes are all computed before it runs and are unaffected by what it says. If the model is wrong, a human reads a badly worded paragraph above a correct table, and nothing acts on it.

So the notification renderer stays deterministic and simply renders the stored text. The model never sits in the delivery path. ADR-023's reasoning is preserved; only its scope is narrowed.

### The model is given facts and nothing else

The prompt receives a fact sheet built from the same structured briefing the email carries, and that sheet is **stored on the snapshot alongside the prose**. Any sentence in the summary can be checked against the exact input it was written from.

The system prompt carries the honesty rules that this platform has accumulated, because they are precisely what a language model gets wrong: inventing a cause, turning "not measured" into "fine", calling a moisture index a percentage, or presenting a proposal as completed work. Rule 2 is the one that matters most — a rise in soil moisture is "consistent with watering", never "you watered it". The platform observed a number; it did not see the greenhouse.

### The deterministic text is always computed, and always the fallback

`BriefingNarrator` produces the same prose from the same numbers, and it runs **first, every time**. The model is then offered the same facts; any failure at all — no key, no credit, no network, a refusal, a timeout — leaves the deterministic version in place.

This is not a nicety. A briefing is the thing that tells someone a plant is dying. It must be produced on a morning when the API is down.

Every briefing records which source wrote it, and a fallback records why. A silently degraded briefing would otherwise look identical to a healthy one.

The feature is **off by default** and needs no key to be absent — a deployment that has never heard of the Anthropic API produces the deterministic briefing and is told nothing is wrong, because nothing is.

## Consequences

**Good:**
- A slow decline is now visible on the day it starts mattering, not the day the plant wilts.
- The briefing opens with something a person can read in fifteen seconds.
- The structured evidence is unchanged and still authoritative; prose is additive.
- The model's input is recorded, so its output can be audited rather than trusted.

**Costs and limits:**
- **The summary can be wrong in ways the tables are not.** The prompt and the fact sheet constrain it heavily, but nothing makes a language model incapable of a plausible false sentence. It is labelled by source for exactly this reason, and it governs nothing.
- **The prose is not testable the way the tables are.** The deterministic narrator's honesty properties are covered by tests; the model's are covered by a prompt and by the fact that it cannot act. That is a genuinely weaker guarantee, and it is the price of the feature.
- A daily API call costs money and adds an external dependency to a system that had none. The fallback bounds the failure, not the cost.
- **Trends need a week of history.** A newly assigned probe reports `UNKNOWN` rather than a trend inferred from two days, and says so.
- The projection is an extrapolation from a linear fit over daily means. It is deliberately crude; a better model of soil drying would be a larger piece of work and is not obviously worth it.
