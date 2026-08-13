# ADR-012: PostgreSQL Remains the Only Application Database

**Status:** Accepted
**Date:** 2026-08-13

## Context

The MCP milestone introduces genuinely new *kinds* of data — crop identity, user intent, harvest events, semantic observations — and it would have been easy to reach for a specialised store for each: a document store for the flexible observation metadata, perhaps a separate store for agent-facing data versus telemetry. The platform's operational foundation (backup/restore, Tailscale-secured access, systemd integration, the local-dev-to-Pi parity established across `docs/operations/`) is built entirely around a single PostgreSQL instance.

## Decision

All new milestone data — `crop`, `goal`, `harvest`, `crop_observation` — lives in the same PostgreSQL instance as everything else, via the same Flyway migration mechanism (`V4` through `V7`) and the same JPA/Hibernate conventions already used throughout the codebase. No graph database, vector database, or document store was introduced.

## Consequences

- The existing backup/restore runbook (`docs/operations/database-backup-and-restore.md`) covers crop data automatically — a single `pg_dump` still captures the entire application state.
- JSONB columns (`goal.metadata_json`, `crop_observation.metadata_json`), already an established pattern from `assessment.evidence_json`, absorb the need for schema-flexible fields without a second storage technology.
- This intentionally defers the question of whether a future milestone (structured decision/outcome history, embeddings for semantic search over observations) will eventually need something PostgreSQL doesn't do well — that question is explicitly out of scope until there's real evidence it's needed.

## Related / superseded decisions

Extends ADR-001. See ADR-013 for the specific case of graph-shaped relationships, which was considered and also resolved in favour of staying on PostgreSQL.
