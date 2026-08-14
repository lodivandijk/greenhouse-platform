# ADR-017: Action Represents Work Performed, Distinct from Observation, Harvest, and Future Control

**Status:** Accepted
**Date:** 2026-08-14

## Context

The crop domain (ADR-009) could already record what was observed (`CropObservation`) and what was produced (`Harvest`), but had no way to record what was *done* — watering, feeding, pruning, hand-pollinating. This gap became concrete with the first indoor strawberry experiment: reconstructing a crop's story requires correlating observations with the actions taken between them (*"soil moisture declining → watered 100ml → leaves more turgid"*), not just facts and outcomes in isolation.

The obvious trap was scope creep: an `Action` model built carelessly could easily grow into a full command/execution engine, a generic outcome framework, or a stand-in for the eventual autonomous control layer — all explicitly out of scope for this platform today (`ARCHITECTURE_PARADIGM.md` §17's "exploratory" category, and the original MCP milestone's own forbidden-scope list).

## Decision

`Action` (`com.greenhouse.action`, its own package — deliberately not folded into `com.greenhouse.crop` alongside `Harvest`/`CropObservation`) persists **what was done**, structurally identical in spirit to `Harvest`'s "value + unit, never a bare number" philosophy:

- A small, extensible `ActionType` enum (`WATER`, `FEED`, `PRUNE`, `POLLINATE`, `MOVE`, `PLANT`, `OTHER`) rather than a free string, matching the established `AssessmentCode`/`GoalType`/`CropObservationMetric` convention.
- `quantity` and `unit` are both optional individually, but **if `quantity` is present, `unit` must be too** — an explicit validation added beyond the spec's literal wording, directly extending the "never store an ambiguous bare number" principle already established for `Harvest`.
- `performedBy` (`HUMAN`/`AGENT`/`AUTOMATION`/`SYSTEM`, defaulting to `HUMAN`) exists specifically so a future automated-control layer can write to the *same* model later, without a schema change — but nothing in this milestone depends on, references, or implies that layer exists. `Action` has zero knowledge of actuators, commands, or execution.
- `performedAt` is independent of `createdAt`, because the user may report an action after the fact ("I watered it this morning") — the same "the record's timestamp may not be when the record was made" pattern already used by `CropObservation.observedAt` and `Harvest.harvestedAt`.

**Package placement**: `Action` sits alongside `Goal` as its own package, not inside `com.greenhouse.crop` alongside `Harvest`/`CropObservation`. Both `Goal` and `Action` are explicitly described (by the user, in both the original milestone spec and this one) as forward-looking concepts that future domains will build on — `Goal` toward a future `Objective`/decision model, `Action` toward a future `Control` layer. `Harvest` and `CropObservation` have no comparable future-facing role; they are simply crop-domain facts. This mirrors the reasoning in ADR-010.

**Ordering**: `list_actions`/`GET /api/v1/actions` return newest-first (reverse chronological), unlike `Harvest`/`CropObservation`, which return oldest-first. This is a deliberate, spec-directed inconsistency: those two exist primarily to read as a timeline (`get_crop_history`), while the primary use case named for `Action` retrieval is "what has been done *recently*" — a recency-first activity feed reads better that way. `get_crop_history` still includes actions in this same order rather than introducing a second query shape purely for internal consistency.

**REST shape**: implemented as the spec's own preferred flat form (`POST /api/v1/actions`, `GET /api/v1/actions`, `GET /api/v1/actions/{id}`, with `cropId` as an optional filter) rather than nesting under `/api/v1/crops/{cropId}/actions` like `Harvest`/`CropObservation`/`Goal`. The spec explicitly offered this as the default, falling back to nested only "if existing project conventions favour it" — and `Action` genuinely has more standalone identity than a harvest weight or an observation value (it's independently meaningful to look up "what was action #42", which is why `GET /api/v1/actions` with no filter is also supported, unlike any other crop-domain listing endpoint).

## Consequences

- `get_crop_history` now composes five sources instead of four (`Crop` + `Goal` + `Action` + `Harvest` + `CropObservation`), reusable by both REST and the `get_crop_history` MCP tool without any new composition logic — `CropHistoryService` already existed for exactly this purpose (ADR-005/ADR-013's precedent).
- Two new MCP tools (`record_action`, `list_actions`), bringing the total tool surface to 16. Both follow the same `McpToolSupport`-mediated error-mapping and domain-service-only-access rules as every other tool (ADR-007).
- `Action` has no delete tool in this pass — not because it was rejected, but because it wasn't asked for. If needed, it would follow the unrestricted leaf-record pattern already established for `delete_harvest`/`delete_crop_observation` (ADR-016), since an `Action` has no children of its own.
- The strawberry-experiment "definition of done" — an action recorded in one MCP session being retrievable from a completely unrelated fresh session — was proven directly in `McpServerIntegrationTest`, not just implied by the architecture: two independent MCP sessions (separate `initialize` handshakes) genuinely round-trip the same persisted action.

## Related / superseded decisions

Extends ADR-009 (crop domain structure) and ADR-010 (future-facing package placement precedent). Reuses the composition pattern from ADR-005/ADR-013. Explicitly does not implement anything from the platform's "exploratory" future direction (`ARCHITECTURE_PARADIGM.md` §10–§16) — no Loop Runtime, no Control, no autonomous execution.
