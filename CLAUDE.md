# Greenhouse Platform

## Architecture documentation rule

This repo uses a `CURRENT_ARCHITECTURE.md` + ADR documentation model, defined in [`docs/architecture/ARCHITECTURE_PARADIGM.md`](docs/architecture/ARCHITECTURE_PARADIGM.md).

Before making a material architectural change:

1. Read [`docs/architecture/CURRENT_ARCHITECTURE.md`](docs/architecture/CURRENT_ARCHITECTURE.md) — the authoritative description of implemented reality.
2. Identify the domain boundary being changed.
3. Check [`docs/architecture/decisions/`](docs/architecture/decisions/) for relevant prior ADRs.
4. Determine whether the change is an implementation detail or an architectural decision.
5. If it's architectural, write a new ADR (`docs/architecture/decisions/ADR-NNN-title.md`) — before or alongside implementing it. Never rewrite an accepted ADR to match later reality; supersede it with a new one instead.
6. Implement the change.
7. Update `CURRENT_ARCHITECTURE.md` only once the implementation reflects the new reality.

Distinguish three categories, per the paradigm doc: **current** (implemented — belongs in `CURRENT_ARCHITECTURE.md`), **decided** (an ADR exists, may or may not be implemented yet), and **exploratory** (discussed but no decision made — must not silently become an implementation assumption). The paradigm doc's own §10–16 (objective-driven reasoning, decision/action loops, LLM reasoning) is exploratory, not current, as of this writing.

`docs/architecture/legacy/` holds superseded architecture docs kept for historical detail — not authoritative, and in places contradicts the current implementation.
