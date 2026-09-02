# ADR-026: A Sensor Outcome Measures Change Against a Pre-Action Baseline

**Status:** Accepted
**Date:** 2026-09-02

## Context

`OutcomeService.evaluateFromSensor` judged whether work succeeded by comparing the earliest and latest soil readings taken *after* the execution completed. It never looked at the soil before the work.

That is not a measurement of the action. It has three separate faults:

1. **No baseline.** With nothing from before the work, there is nothing to attribute a change to.
2. **Drift is credited to the action.** Two post-action readings differ mostly because soil dries, so normal drying was scored as the outcome of watering.
3. **The window was not enforced.** The query had no upper bound, so readings arriving after the scheduled evaluation window still influenced the result. `evaluationWindowEnd` was stored on every outcome and read in exactly one place — to copy itself onto the outcome record. The stored window was decorative.

The practical effect is worse than a coin flip. A probe responds to watering within a minute and then dries back over a twelve-hour window, so the last reading is drier than the first. Running the old arithmetic on realistic values — baseline index 7, wet peak 87, settling at 44 — the old code computed a change of **−42.9** and recorded `FAILED`. Successful watering was systematically labelled a failure.

This matters more than an ordinary bug because outcome labels are the evidence base this platform exists to accumulate. The stated long-term goal is learning which growing plans work. A model trained on this history would learn that watering dries soil out.

## Decision

An outcome measures the difference between the soil **before** the work and the soil **during the stored evaluation window**.

- **Baseline** is the latest reading at or before `execution.completedAt`. If none exists, the outcome is `INCONCLUSIVE` with reason `NO_BASELINE_READING` — never a guess.
- **Comparison** uses only readings inside `[evaluateAfter, windowEnd]`, bounded at both ends, so the window recorded on the outcome is the window actually evaluated.
- **The peak index within the window** is what is compared against the baseline. Comparing only the final reading would re-introduce the original bug in a smaller form: soil that got properly wet and then dried back to slightly above baseline would score as barely responding. The wettest moment in the window is the fairest test of whether water reached the soil at all.
- **Both are reported.** Evidence carries `baselineMoistureIndex`, `peakMoistureIndexInWindow`, `finalMoistureIndexInWindow`, their timestamps, the number of readings considered, and the calibration id and version used — so a later recalibration cannot silently change what an old outcome appears to have measured.
- A change within `NOISE_TOLERANCE_INDEX_POINTS` (1.0) in either direction is `PARTIAL`, not success or failure. Probe readings jitter by a point or two between samples and that is not evidence of anything.

## Consequences

**Good:**
- A successful watering is recorded as `SUCCESS`. Verified directly: identical inputs produce `FAILED` under the old logic and `SUCCESS` under the new.
- The evaluation window is enforced rather than described.
- `INCONCLUSIVE` now covers the genuinely unmeasurable cases — no baseline, no readings in window — instead of those cases silently producing a confident wrong label.
- Every outcome carries enough evidence to re-derive its own verdict, including which calibration produced the numbers.

**Costs and limits:**
- **More outcomes will be `INCONCLUSIVE`**, particularly for work done on a crop whose probe was assigned afterwards. This is the correct answer, but it means the evidence base grows more slowly than it appeared to before — the previous rate was partly fictional.
- **Outcomes recorded before this change are wrong and are not corrected.** They are immutable records of what the engine concluded at the time. They should be treated as unusable for learning, and a future correction pass could supersede them via the existing `supersedesOutcomeId` linkage rather than editing them.
- **The peak is still an inference, not proof of causation.** Rain through an open vent, or a second person watering, would look identical. Sensor evidence tells you the soil got wetter, not why. `HUMAN_CONFIRMED` and `HYBRID` exist for exactly that reason.
- Only watering has a meaningful expected direction today. Any future action type with a different expected soil response needs its own comparison, not this one.
