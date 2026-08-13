# ADR-014: Fresh Agent Session Is an Architectural Test Boundary

**Status:** Accepted
**Date:** 2026-08-13

## Context

The development Claude Code session building this milestone already has extensive context: it has read the source code, knows the database schema, remembers every prior architectural decision in this repository. Testing the MCP tool surface from within that same session would not honestly test what the milestone is actually meant to deliver — a tool surface usable by an AI client that knows nothing about the implementation. A development session's success at using the tools proves the tools are internally consistent; it does not prove they are *understandable from the outside*.

## Decision

The milestone's primary acceptance test (`docs/mcp/AGENT_SETUP.md`) requires validating MCP from a genuinely clean Claude Code repository — a new, empty directory containing only `CLAUDE.md` and `.mcp.json`, with no access to `greenhouse-platform`'s source, git history, or this conversation. That clean session must independently discover tools, inspect state, create a crop, set a goal, record a harvest, record an observation, and summarise crop history using only what the MCP tool descriptions and schemas tell it.

## Consequences

- The MCP tool descriptions had to be written assuming zero repository context: enum values spelled out inline, default behaviour (e.g. `plantedAt` defaulting to now) stated explicitly, and which tool to use for current state versus history made unambiguous — because a clean session has nothing else to go on.
- Any tool that the clean-agent test finds confusing or insufficient is direct evidence the tool model needs improvement, not the agent — this reframes "the AI didn't understand it" as a design defect to fix, not a prompt-engineering problem to route around.
- This test cannot be automated away entirely: `docs/mcp/AGENT_SETUP.md` documents the exact manual procedure so it can be re-run whenever the tool surface changes materially.

## Related / superseded decisions

Validates ADR-007 in practice — if the capability boundary in ADR-007 is well-designed, a context-free client should be able to use it correctly.
