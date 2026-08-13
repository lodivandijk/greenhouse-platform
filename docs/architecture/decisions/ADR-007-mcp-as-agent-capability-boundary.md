# ADR-007: MCP as the Agent Capability Boundary

**Status:** Accepted
**Date:** 2026-08-13

## Context

The platform needed to become usable conversationally: a Claude session should be able to inspect greenhouse state and record crop knowledge without any prior knowledge of this repository, its database schema, or its Java implementation. The naive approaches were both rejected up front by the milestone spec itself: giving an LLM raw database or shell access, or building a bespoke chat integration bolted onto the existing REST API.

## Decision

Expose a deliberately small, curated set of MCP tools (`GreenhouseStateTools`, `CropTools`, `GoalTools`, `HarvestTools`, `CropObservationTools` in `com.greenhouse.mcp`) as the *only* way an AI client can interact with the platform's crop/greenhouse capabilities. Every tool has one clear purpose, validates its inputs, and returns structured, domain-language output — never raw SQL, JPA exceptions, or filesystem/shell access. Tools the spec explicitly forbids (`execute_sql`, `query_database`, `run_shell`, `write_file`, `execute_gpio`, `raw_repository_access`) were simply never built.

## Consequences

- Agents depend on stable tool contracts (name, JSON schema, description), not on implementation details that are free to change underneath them.
- New tool exposure is always a deliberate, reviewed addition — there is no mechanism that auto-exposes arbitrary Spring endpoints as MCP tools.
- Multiple future AI clients (not just Claude Code) can use the same MCP surface without any of them needing repository access.

## Related / superseded decisions

Depends on ADR-005 (`/api/v1/state`) and the whole facts/interpretation chain established by ADR-002 through ADR-004 — MCP tools consume those same trusted domain services, they don't reinterpret raw data themselves. See ADR-008 for where the server runs, and ADR-015 for which MCP library implements the transport.
