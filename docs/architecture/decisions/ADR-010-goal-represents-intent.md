# ADR-010: Goal Represents Intent, Not Executable Control

**Status:** Accepted
**Date:** 2026-08-13

## Context

A user describing what they want from a crop ("I want as much usable foliage as possible for as long as possible") is expressing an outcome, not a control instruction. Nothing in the current platform can act on the physical greenhouse — there is no actuator control, no irrigation/heating/ventilation integration, and the milestone spec explicitly forbids building any of that here. Persisting the user's intent as if it were already an executable objective would misrepresent what the system can actually do.

## Decision

`Goal` (`com.greenhouse.goal`) persists intent only: `goalType` (a small enum — `MAXIMISE_LONG_TERM_YIELD`, `MAXIMISE_FOLIAGE`, `MAXIMISE_FLOWERING`, `MAXIMISE_FRUIT_QUALITY`, or `OTHER` with a required `description`), plus `sourceInstruction` preserving the user's own words verbatim where supplied. `GoalStatus` is limited to `ACTIVE`/`COMPLETED`/`CANCELLED`. There is no field, method, or MCP tool that turns a Goal into a scheduled action.

The enum was chosen over an unconstrained string specifically to match this codebase's existing convention (`AssessmentCode`, `AssessmentSeverity` are both small closed enums, not strings) — with `OTHER` as the deliberate escape hatch the spec asked for, so a goal type nobody anticipated can still be recorded without a schema change, as long as it comes with a human-readable description.

## Consequences

- Vague or aspirational goals can be safely recorded even though nothing downstream can act on them yet.
- The `create_goal` MCP tool description explicitly tells the model not to invent automated control objectives or claim the greenhouse can perform actions that aren't exposed as tools — this ADR is the reasoning behind that instruction.
- When a future milestone introduces an executable `Objective`/decision loop, it will consume `Goal` as an input to reasoning, not replace it — `Goal` stays the durable record of "what the user asked for," independent of however reasoning about it evolves.

## Related / superseded decisions

Related to ADR-009 (Goal lives in its own package, separate from Crop, deliberately). Will be built upon, not superseded, by any future Objective/decision-loop ADR.
