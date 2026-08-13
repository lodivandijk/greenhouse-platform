# Greenhouse Platform Architecture

## Purpose

This section describes the architecture of the Greenhouse Platform, using the model defined in [`ARCHITECTURE_PARADIGM.md`](ARCHITECTURE_PARADIGM.md):

- **[`CURRENT_ARCHITECTURE.md`](CURRENT_ARCHITECTURE.md)** — what the deployed system looks like right now. Authoritative for current reality; read this first.
- **[`decisions/`](decisions/)** — Architecture Decision Records (ADRs) explaining *why* the system looks the way it does. Historical records — once accepted, an ADR is never rewritten, only superseded by a new one.
- **[`legacy/`](legacy/)** — earlier architecture documents, superseded by the two above. Kept for point-in-time detail and historical rationale; not authoritative, and some of it now contradicts the current implementation.
- **[`ARCHITECTURE_PARADIGM.md`](ARCHITECTURE_PARADIGM.md)** — the documentation model itself, plus the platform's longer-term direction (objective-driven reasoning, decisions, actions). That direction is exploratory, not yet implemented — see the paradigm doc's own "Current vs Future" distinction before treating any of it as existing behaviour.

Implementation specs for individual milestones (`digital-twin-v1-spec.md`, `assessment-engine-v1-spec.md`, `ui-v1-spec.md`, `mcp-agent-milestone-v1-spec.md`) remain at the top level of this directory — they're point-in-time acceptance-criteria documents that informed the ADRs, not architecture descriptions themselves.

## Documents

```text
docs/architecture/
├── README.md                        (this file)
├── ARCHITECTURE_PARADIGM.md          the documentation model + future direction
├── CURRENT_ARCHITECTURE.md           what's implemented and deployed now
├── decisions/                        ADRs — why it looks like this
│   ├── ADR-001-persist-observations.md
│   ├── ADR-002-introduce-digital-twin.md
│   ├── ADR-003-twin-facts-only.md
│   ├── ADR-004-separate-assessment-engine.md
│   ├── ADR-005-compose-application-state.md
│   ├── ADR-006-vanilla-static-ui.md
│   ├── ADR-007-mcp-as-agent-capability-boundary.md
│   ├── ADR-008-mcp-hosted-in-existing-runtime.md
│   ├── ADR-009-crop-domain-without-reworking-telemetry.md
│   ├── ADR-010-goal-represents-intent.md
│   ├── ADR-011-flexible-crop-observation-metrics.md
│   ├── ADR-012-postgresql-only-database.md
│   ├── ADR-013-graph-as-logical-projection.md
│   ├── ADR-014-fresh-agent-session-test-boundary.md
│   └── ADR-015-mcp-java-sdk-not-spring-ai-starter.md
├── legacy/                           superseded documents, kept for history
├── digital-twin-v1-spec.md           milestone spec
├── assessment-engine-v1-spec.md      milestone spec
├── ui-v1-spec.md                     milestone spec
└── mcp-agent-milestone-v1-spec.md    milestone spec
```

## Development rule

Before making a material architectural change: read `CURRENT_ARCHITECTURE.md`, identify the domain boundary being touched, check `decisions/` for relevant prior ADRs, and — if the change is itself an architectural decision rather than an implementation detail — write a new ADR before or alongside implementing it. Update `CURRENT_ARCHITECTURE.md` only once the implementation actually reflects the new reality. Full detail in `ARCHITECTURE_PARADIGM.md` §18.
