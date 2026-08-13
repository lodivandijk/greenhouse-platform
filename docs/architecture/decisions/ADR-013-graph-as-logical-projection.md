# ADR-013: Graph Remains a Logical Projection, Not a Graph Database

**Status:** Accepted
**Date:** 2026-08-13

## Context

`get_crop_history` composes a genuinely graph-shaped view — a crop connected to its goals, harvests, and observations, each with their own timestamps, forming a timeline an AI reasons over. This is exactly the kind of shape that motivates reaching for a graph database. The architecture paradigm's longer-term direction (`ARCHITECTURE_PARADIGM.md` §14–15) also explicitly anticipates future reasoning over relationships between durable entities (Objective, Decision, Outcome, ...).

## Decision

No graph persistence was introduced. `CropHistoryService` (`com.greenhouse.crop`) composes the graph-like `CropHistoryResponse` at query time from ordinary relational lookups (`GoalRepository.findAllByCropIdOrderByCreatedAtAsc`, etc.), the same compositional pattern already established by `GreenhouseStateService` (ADR-005) for twin+assessment. The "graph" that an AI client sees is a response shape produced by a service, not a persistence technology.

## Consequences

- Relationships stay simple foreign keys and repository queries — inspectable with plain SQL, covered by the same backup strategy as everything else (ADR-012).
- If a future milestone's reasoning genuinely needs graph traversal that relational queries can't express reasonably (multi-hop relationship queries across many entity types), that would be a new, separately-justified ADR — not something this milestone pre-emptively built for.
- Keeps `get_crop_history`'s output bounded and predictable: one crop, its goals, its harvests, its observations — not an open-ended graph walk an LLM could accidentally make expensive.

## Related / superseded decisions

Extends the composition pattern from ADR-005. Depends on ADR-012 (no graph database).
