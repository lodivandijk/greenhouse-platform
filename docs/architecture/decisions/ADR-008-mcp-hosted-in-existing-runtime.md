# ADR-008: MCP Hosted Inside the Existing Spring Boot Runtime

**Status:** Accepted
**Date:** 2026-08-13

## Context

The platform runs as one Spring Boot application (`greenhouse-platform.jar`) on the Raspberry Pi, deployed via a single-artifact `deploy.sh` that already handles build, transfer, cutover and health-checking. Adding MCP support could have meant a second deployed process (its own systemd unit, its own port, its own way of reaching the domain services).

## Decision

Host the MCP server inside the existing application. `WebMvcStreamableServerTransportProvider` is wired as ordinary Spring beans (`McpServerConfiguration`) exposing a `RouterFunction` alongside the existing REST controllers, reachable at `POST /mcp` on the same port (8080) as everything else.

## Consequences

- One deployment artifact, one systemd unit, one health check, one `deploy.sh` run — the entire operational playbook built up over the Remote Pi work (`docs/operations/`) applies unchanged.
- MCP tools call the same `@Service` beans (`CropService`, `GoalService`, `GreenhouseStateService`, etc.) directly, in-process — no network hop, no second copy of business logic, no risk of the two surfaces drifting apart.
- No new distributed-systems failure mode was introduced (no second process to keep alive, no inter-process auth to design).
- The MCP endpoint necessarily shares fate with the rest of the application: if the JVM is down, both REST and MCP are down together. This was judged acceptable — the two surfaces are used by the same operator on the same greenhouse.

## Related / superseded decisions

Depends on ADR-007. See ADR-015 for the specific library used to implement the transport within this process.
