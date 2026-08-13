# ADR-016: Scoped Delete Capability for the Crop Domain

**Status:** Accepted
**Date:** 2026-08-13

## Context

The MCP Agent Milestone shipped without any delete capability — the original spec never mentioned deletion at all, unlike the things it explicitly deferred (Loop Runtime, actuators, etc.). In practice this meant every mistaken or test-only crop record created during this project's own verification work had to be cleaned up via direct `psql` access, which is exactly the kind of raw-database operation this milestone's whole design (ADR-007) exists to keep out of the agent-facing surface. A real, scoped delete capability was needed.

Deletion is qualitatively different from every other tool added so far: `create_*`/`record_*`/`update_*` are all either additive or corrective-by-addition — nothing they do is hard to reverse. A poorly-scoped `delete_crop` callable from a natural-language conversation could destroy a season's worth of harvest and observation history from a single ambiguous sentence, with no undo.

## Decision

Two different safety models, chosen deliberately per entity:

- **`delete_crop`**: hard delete, but **only permitted when the crop has zero goals, harvests, and observations** (`CropService.deleteCrop`, checked via `existsByCropId` on each child repository). Any crop with real recorded history must be retired via `update_crop` (`status: ENDED`) instead, or have its child records removed first. This makes `delete_crop` strictly a "fix a mistake" tool — it is structurally incapable of destroying real history, no matter what a conversation asks it to do.
- **`delete_goal` / `delete_harvest` / `delete_crop_observation`**: unrestricted hard delete of a single leaf record. These have no children of their own (nothing references a `Goal`, `Harvest`, or `CropObservation`), so the blast radius of deleting one is inherently small and well-understood — removing one mistaken entry, not an aggregate's worth of history.

Both REST (`DELETE /api/v1/crops/{cropId}`, etc.) and the four new MCP tools call these same service methods directly, per the existing REST/MCP layering rule (ADR-007). Every delete method returns the deleted record's last state before removal, so both REST responses and MCP tool results confirm exactly what was removed.

## Consequences

- The one destructive capability in this system cannot silently erase a real crop's history through conversation — it will refuse and explain why (`"Crop 42 has recorded goals, harvests, or observations and cannot be deleted..."`), giving the agent (and the ADR-014 fresh-agent test) a chance to ask the user whether they meant to retire it instead.
- Cleaning up a genuinely empty mistaken crop, or a single bad harvest/observation/goal entry, no longer requires direct database access — the exact gap that prompted this ADR.
- There is deliberately no bulk delete, no cascade-delete option, and no soft-delete/undo mechanism. If a real need for recovering an accidentally-deleted leaf record emerges, that's a new, separately-justified decision — soft-delete was considered and rejected for this pass specifically because it would have touched every existing crop-domain query to add a "not deleted" filter, for a capability not yet shown to be needed.
- `CropService` now depends on `GoalRepository` (from the separate `com.greenhouse.goal` package) for the emptiness check, mirroring the existing, symmetric `GoalService → CropRepository` dependency from ADR-009/ADR-010 — a narrow, existence-check-only relationship in both directions, not a broader coupling.

## Related / superseded decisions

Extends ADR-007 (deliberate tool exposure) and ADR-009 (crop domain structure). Does not change ADR-010's (Goal-as-intent) or ADR-011's (flexible metrics) reasoning — deletion is orthogonal to what gets recorded, only to how it can be un-recorded.
