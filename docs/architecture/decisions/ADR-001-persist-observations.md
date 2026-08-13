# ADR-001: Persist Observations to PostgreSQL

**Status:** Accepted
**Date:** 2026-07-18

## Context

The first working version of the platform (iteration 2) accepted BME280 environmental readings from the ESP32 over HTTP and held only the single latest observation per device in memory (mirroring the same in-memory pattern already used for device/heartbeat state at the time). This was sufficient to answer "what is the greenhouse doing right now?" but had two problems:

- Every restart of the Spring Boot process silently discarded all environmental history.
- There was no way to answer "what happened over the last hour/day?" — a question that becomes necessary for anything beyond a live dashboard (trend detection, later reasoning, debugging a sensor issue after the fact).

## Decision

Introduce PostgreSQL as the system of record for observations, via a new `observation` table (`V1__create_observation_table.sql`) and a standard Entity → Repository → Service → Controller layering (`ObservationEntity`, `ObservationRepository`, `ObservationService`, `ObservationController`), managed with Flyway migrations and `spring.jpa.hibernate.ddl-auto=validate` (schema changes only ever happen through a migration, never through Hibernate auto-DDL).

Observations are modelled as an **immutable, append-only log**: every accepted reading gets its own row with a synthetic `BIGINT` id and a `received_at` timestamp. Nothing is ever updated or deleted by the ingestion path. "Latest observation" becomes a query (`ORDER BY received_at DESC LIMIT 1`), not a separately maintained field.

The REST contract (`POST /api/v1/observations`, `GET /api/v1/observations/latest`, `GET /api/v1/observations/{deviceId}`) did not change shape from the in-memory version — only the storage underneath it did.

## Consequences

- The platform now retains a genuine historical environmental record, not just a live snapshot. This became a prerequisite for everything built afterward (the Digital Twin's freshness/staleness calculations, the Assessment Engine's evaluation cycle) and is expected to matter more as the platform moves toward the reasoning/optimisation direction described in the architecture paradigm.
- Local development and the Raspberry Pi deployment both require a running PostgreSQL instance; this added real operational surface (installation, credentials, backup/restore) that didn't exist before.
- Because observations are append-only, the table grows unboundedly. No retention/rollup policy exists yet — this is an accepted limitation, not an oversight.

## Related / superseded decisions

The device registry (previously an in-memory `ConcurrentHashMap`, same problem as observations) was migrated to PostgreSQL shortly afterward using the same layering pattern, but as a single mutable row per device rather than an append-only log — a deliberate, separately-reasoned trade-off (current-state snapshot is sufficient for device connectivity; historical device metadata trends were not yet needed). This was not judged to warrant its own ADR at the time; revisit if device history becomes materially important.

Superseded by: none.
