# Legacy architecture documents

Everything in this folder is **superseded**. It's kept because it contains useful point-in-time detail (early design rationale, PostgreSQL learning notes, the original remote-Pi access spec), but none of it should be treated as describing the system as it exists today — some of it actively contradicts the current implementation (e.g. `digital-twin-architecture.md` still describes the twin as containing "derived assessments," which ADR-003 removed).

For current reality, see [`../CURRENT_ARCHITECTURE.md`](../CURRENT_ARCHITECTURE.md).
For why things changed, see [`../decisions/`](../decisions/).

| File | What it was |
|---|---|
| `system-context.md` | Early C4-style system context diagram |
| `container-architecture.md` | Early C4-style container diagram |
| `component-architecture.md` | Early C4-style component diagram |
| `deployment-architecture.md` | Early deployment topology notes |
| `data-flow.md` | Early data-flow narrative |
| `domain-model.md` | Early domain model sketch |
| `architecture-roadmap.md` | Early forward-looking roadmap (see `ARCHITECTURE_PARADIGM.md` for the current version of this) |
| `data.md` | Iteration 3/4 persistence design notes (device + observation schema reasoning) |
| `postgresql-design.md` | Early PostgreSQL adoption rationale |
| `postgresql-implementation-plan.md` | Iteration 3 PostgreSQL rollout plan |
| `postgresql-learning-notes.md` | Working notes from the PostgreSQL rollout |
| `remote-pi.md` | The original Remote Pi Access readiness spec (Tailscale, SSH hardening, systemd, backups) — still the operational basis for `docs/operations/`, but the design record now belongs here rather than under active architecture |
