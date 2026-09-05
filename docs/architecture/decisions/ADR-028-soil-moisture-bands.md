# ADR-028: Soil Thresholds Are Set as a Band, Per Crop

**Status:** Accepted
**Date:** 2026-09-05

## Context

The mint wilted visibly at moisture index 43.6 while its dry threshold was 30. Its index had fallen steadily for six days — 78, 74, 67, 62, 50, 46, 44 — without ever crossing the line, so no assessment was raised and nothing was said. The threshold came from a spec table written before any plant was in the greenhouse and had never been checked against one.

V31 corrected the numbers. But that migration exposed the real problem: there was no way to change a threshold without a code change and a deployment, and the numbers themselves had no stated basis. Someone reading `soil_dry_threshold_index = 30` a year later could not tell whether that was measured, reasoned, or guessed.

The first design was a tool taking raw threshold numbers per crop. That is worse than it looks. A per-crop free number is one nobody can justify later, it invites drift between crops that ought to be treated the same, and it puts the burden of picking a defensible value on whoever happens to be asking — including an LLM with no horticultural grounding.

## Decision

A crop is assigned a **soil moisture band** describing how thirsty it is judged to be. The band determines the thresholds.

| Band | Dry | Wet | Meaning |
|---|---|---|---|
| `HIGH` | 50 | none | Wants consistently moist soil (basil, mint) |
| `MEDIUM` | 40 | 85 | Moderate, even moisture |
| `LOW` | 30 | 75 | Drought-tolerant, dries out between waterings (thyme, sage, oregano, tarragon) |

`HIGH` means thirstiest — flagged soonest — not "high threshold" in the abstract.

`HIGH` has no wet ceiling deliberately. A moisture-loving herb is not harmed by the wet end the way a Mediterranean one is, and a ceiling nobody needs is a source of false alarms that teaches people to ignore the real ones.

### Set per crop, not per species

Two mint plants in different corners of the same greenhouse dry at different rates, and the one by the door is the one that wilts. The band is a property of the plant in its position, not of the species. V32 assigns bands by strategy because that happens to match the current six crops, but nothing in the model ties a band to a species.

### The resolved numbers are stored, not just the band

Each profile version records both the band and the thresholds it resolved to. The assessment rule keeps reading plain numbers and knows nothing about bands.

This matters for a specific reason: a band's definition is code, and code changes. If only the band were stored, redefining `LOW` next spring would silently rewrite what every historical assessment meant. Storing the resolved numbers means a two-year-old assessment still shows the exact thresholds that produced it — the same principle that made monitoring profiles versioned in the first place (ADR-021).

### Adding a band is a deliberate change

A fourth band — `VERY_HIGH`, say — means adding the constant with its thresholds and writing a migration that assigns it. That is intentionally more friction than editing a number. The friction *is* the mechanism: it forces the question "which crops belong in this new band, and why" to be answered once, in a reviewable place, rather than implicitly and differently for each crop.

Nothing else has to change when a band is added. A test asserts every band produces a range the profile validator accepts, and that thirstier bands are flagged as dry sooner, so a badly-defined new band fails at build time rather than the first time someone uses it.

### No raw-threshold tool

`set_crop_soil_moisture_band` is the only way to change soil thresholds. There is deliberately no MCP tool taking a raw number. If no band fits a crop, the correct response is to say so and add a band — not to force the nearest one, and not to reach around the model.

The service retains the ability to write arbitrary thresholds through `createVersion`, which is how migrations seed profiles. That is a deliberate asymmetry: a migration is reviewed and versioned; a tool call is not.

## Consequences

**Good:**
- A threshold change is now a conversation ("this one is thirstier than we thought") rather than a deployment.
- Crops in the same band are demonstrably held to the same standard, and re-tuning that standard re-tunes all of them together.
- Every change records a rationale and creates a new version, so "why is this crop flagged at 50?" is answerable from the data.
- The numbers exist in exactly one place in code.

**Costs and limits:**
- **Three bands will not fit every plant.** That is the trade: less expressive than free numbers, in exchange for values anyone can justify. When a crop genuinely does not fit, the answer is a new band, which needs a deploy.
- **V32 moves thyme's wet ceiling from 80 to 75**, aligning it with the other Mediterranean herbs. Every other crop keeps the thresholds it already had. Thyme is marginally more likely to be reported as too wet, which is the direction it is currently failing in anyway.
- **`SoilMoistureBand` and `SoilMoistureStrategy` overlap.** The strategy (`EVENLY_MOIST` / `DRY_BETWEEN_WATERING`) decides which direction is *concerning* — it drives assessment severity — while the band decides the *numbers*. Today they map one-to-one, which is a smell: `HIGH` is always `EVENLY_MOIST`, `LOW` is always `DRY_BETWEEN_WATERING`. They are not collapsed yet because they answer different questions and the mapping may not hold once `MEDIUM` is used. This should be revisited rather than left to drift.
- Historical profile versions have a null band. They predate the concept, and inventing one for thresholds that were never chosen as a band would be fiction in a table whose job is explaining past assessments.
