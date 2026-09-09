# ADR-030: One Briefing Summary Writer, and None Is an Acceptable Answer

**Status:** Accepted
**Date:** 2026-09-09
**Supersedes:** the deterministic-fallback decision in ADR-029. ADR-029's narrowing of ADR-023 stands unchanged.

## Context

ADR-029 kept a deterministic sentence assembler alongside the language model: it ran first every morning, and any model failure left its text in place. The reasoning was that a briefing is what tells someone a plant is dying, so it must be produced when the API is down.

A week of running it showed the reasoning was right but the conclusion was wrong, for two reasons.

**The fallback was never protecting the briefing.** It was protecting the opening paragraph. The greenhouse conditions, per-crop soil readings, trends, warnings, open care loops and data gaps are all computed before the summary runs and are emitted whatever happens to it. The thing that tells you a plant is dying is the trend table, and it never depended on the narrator.

**The fact sheet was made of prose.** The narrator's sentences were what got handed to the model, so the model was rewriting another author's prose rather than reading the data. That is worse than the duplication: it put a second writer between the readings and the reader, and any imprecision in the first author's phrasing was inherited by the second.

There was also the cost the user named directly — two prose paths, both needing to stay honest, both needing tests, drifting apart the moment one is edited.

## Decision

**Delete the deterministic writer.** `BriefingNarrator`, `CropNarrativeInput` and their tests are removed outright. The language model writes the summary or nobody does.

**Build the fact sheet from structured data.** Each crop contributes a `factLine` assembled from its actual values rather than from sentences:

```
- Thyme (crop 9): moisture index 36, dry line 30, wet ceiling 75, band LOW.
  Trend: falling, -3.2 index points/day over 7 days (60 to 38).
  A sharp rise occurred within that window (cause unobserved).
  Extrapolated 1.9 days to the dry line if the rate holds.
```

Terse, fixed in shape, and traceable field by field. "Cause unobserved" is stated in the data itself rather than left to the prompt to remember — the platform saw a number move, not a person with a watering can.

**A missing summary is stated, not skipped.** When no writer is configured, or the call fails, the briefing carries `unavailableReason` and the email prints *"No summary this morning — … The readings below are unaffected."* A briefing that quietly began at the tables would be indistinguishable from a morning with nothing to say, which is the same failure mode as a silently-absent data gap.

**The configured writer is announced at startup.** A `WARN` when none is configured, an `INFO` naming the model when one is. This exists because of how this ADR came to be written: the summary properties were bound at `greenhouse.briefing.summary` while `application.yml` declared them under `greenhouse.daily-briefing.summary`, so setting `GREENHOUSE_BRIEFING_LLM_ENABLED=true` did nothing at all. The composer bean was never created, `getIfAvailable()` returned null, and the briefing fell back with **no** `fallbackReason` — the signature of "never switched on" rather than "tried and failed". Nothing in the logs distinguished the two. A feature that is enabled in the environment and inert in the application must announce itself.

## Consequences

**Good:**
- One writer, one prompt, one set of honesty rules. Nothing to keep in sync.
- The model reads data rather than someone else's sentences.
- The removal is a net deletion: two classes and a test file gone, the fact sheet simpler than the prose it replaced.
- "No summary" is now a visible state rather than an absence.

**Costs and limits:**
- **A briefing on a bad morning has no paragraph.** The readings, trends and warnings are all still there, so nothing that matters is lost — but the fifteen-second read at the top is gone precisely when someone may be skimming. This is the trade being made deliberately, and it is the part most worth revisiting if it turns out to bite.
- **The honesty properties are no longer unit-testable the way they were.** ADR-029 already flagged this; removing the deterministic writer removes the last component whose exact wording could be asserted. What remains is the prompt, the structured fact sheet, and the fact that the summary governs nothing.
- **An external dependency now sits in the daily path** with no in-process substitute. The failure is bounded to one paragraph, but it is a real dependency where there was none.
- The `DeterministicNotificationRenderer` keeps its name. It renders stored content deterministically, which is still exactly what it does — it is the delivery path ADR-023 protects, and no model has ever run inside it.
