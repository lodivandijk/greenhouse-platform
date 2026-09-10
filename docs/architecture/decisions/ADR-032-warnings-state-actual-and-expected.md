# ADR-032: A Warning States What Was Measured and What Was Expected

**Status:** Accepted
**Date:** 2026-09-10
**Amends:** ADR-031's "a push carries no measurements" rule.

## Context

ADR-031 kept every number out of push notifications. The reasoning was about one number in particular: the soil moisture index is a position between a single probe's own calibrated dry and wet references, not a percentage, and two probes reading 40 are not equally wet. The caveat that makes it honest does not fit on a lock screen, so the number was left out.

Applied to everything, that was too blunt. A temperature in degrees Celsius needs no caveat — 25°C against a preferred maximum of 24°C is complete and unambiguous on its own. Withholding it produced alerts that said *something is wrong with this crop* and made the reader open an email to find out what, which is most of the value of a push gone.

The user asked for the crop, the expected value and the current value, with the shape:

> `greenhouse-01 Crop 14 Oregano temperature 25C above preferred limit of 24C`

## Decision

Warnings state **what was measured, on what, against what it should have been** — in both channels.

Everything needed was already recorded. The assessment engine writes its evidence when it raises an assessment (`actualTemperatureCelsius` / `preferredMaximumCelsius`, `moistureIndex` / `dryThresholdIndex`, `actualHumidityPercent` / `maximumHumidityPercent`), and the notification payload already captured it. The numbers in a warning are therefore the same numbers that caused it, not a re-reading taken later that might disagree.

### The index keeps its scale, everywhere it appears

ADR-031's concern was right about the moisture index specifically, so it is met rather than abandoned. The index is never rendered bare and never with a percent sign:

```
soil 43 of 100, at or below its dry line of 50
soil 100 of 100, at or above its wet ceiling of 75
```

"of 100" makes it a position on a scale in the four words a glance affords. A test asserts the string `43%` never appears.

Temperature and humidity carry their real units and need no such handling.

### Species and greenhouse id are captured at intent time, not looked up at render time

The renderer reads only the intent's payload — that property is what lets a message read as it was meant even after the greenhouse has moved on (ADR-023). So `speciesFor(cropId)` runs in the policy service when the intent is created, and a later rename or deletion cannot change what an old notification says it was about.

Payloads written before this change carry neither field. The renderer degrades to `crop 12` rather than failing, and a test covers that case — a backlog of older intents is exactly what a newly enabled channel replays.

### One measurement line, both channels

Email and push build the same line from the same helper. The email keeps the assessment engine's own sentence beneath it, plus the profile and calibration versions; the push carries the line alone. Two renderings that disagreed about a number would be worse than either.

Every flagged crop gets its own line. A shared greenhouse temperature loop can cover several crops with different preferred maxima, and naming only the first would hide the rest.

An unrecognised assessment code falls back to a humanised form of the code itself rather than rendering an empty line — a future code must degrade to *saying less*, never to *saying nothing*.

## Consequences

**Good:**
- A push is now actionable on its own: what, where, how far out.
- The numbers are the ones that raised the assessment, so a warning cannot disagree with the record it came from.
- The moisture index's ambiguity is handled where it actually lives — in how the number is written — rather than by suppressing it.

**Costs and limits:**
- **Push messages are longer**, and a multi-crop loop produces several lines. Still far shorter than the email, but the two-line ideal is gone.
- **Two renderings now share a helper**, so a change to the phrasing touches both. That is the intent — they must agree — but it is coupling that did not exist before.
- **The evidence keys are matched by string.** A rule that renames `actualTemperatureCelsius` would silently produce "temperature unknown" rather than failing. The fallback is honest, but nothing catches the rename.
- Older intents render without species or greenhouse id. They age out.
