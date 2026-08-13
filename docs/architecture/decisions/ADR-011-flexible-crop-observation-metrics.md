# ADR-011: Flexible Crop Observation Metrics

**Status:** Accepted
**Date:** 2026-08-13

## Context

Different crops and goals call for different biological evidence — stem woodiness for a basil plant being harvested for foliage, Brix for a fruiting crop, flower count for something grown for blooms. Adding a database column per characteristic (`crop_observation.stem_woodiness`, `crop_observation.flower_count`, ...) would require a schema migration for every new kind of observation anyone ever wants to record, and would leave most rows mostly `NULL`.

## Decision

`CropObservation` uses a strict envelope — `crop_id`, `metric`, a `value_type` discriminator, `source`, `observed_at` are always present — around a flexible metric vocabulary. `CropObservationMetric` is a Java enum (`PLANT_HEALTH`, `STEM_WOODINESS`, `FLOWER_COUNT`, `FLOWERING_STAGE`, `LEAF_COLOR`, `GROWTH_RATE`, `DISEASE_SIGNS`, `SWEETNESS_SCORE`, `BRIX`, plus `OTHER`) rather than a free string, and the value itself is one of three explicitly-typed columns (`numeric_value`/`text_value`/`boolean_value`) selected by `value_type`, not a single opaque JSON blob.

## Consequences

- New observation *instances* never require a migration — only a genuinely new *kind* of metric not covered by the existing vocabulary (including `OTHER`) would. No database-driven metric-definition subsystem was built, matching the spec's explicit instruction not to over-build this for v1.
- The service layer (`CropObservationService.recordObservation`) enforces that exactly one of the three value fields is populated and that it matches the declared `value_type` — this rule is centralized once, applied identically whether the caller is the REST API or an MCP tool.
- `unit` and `confidence` are optional context on top of the envelope, not part of the discriminated value itself.
- The MCP tool schema for `record_crop_observation` had to expose `numericValue`/`textValue`/`booleanValue` as three separate optional fields (see ADR-015-adjacent tool-design notes in `docs/mcp/IMPLEMENTATION.md`) rather than one polymorphic field, since JSON Schema has no ergonomic way to say "this field's type depends on a sibling field's value" without `if`/`then` schemas that are needlessly complex for ten tools total.

## Related / superseded decisions

Extends the envelope pattern already used by `AssessmentEntity.evidence` (JSONB) — but deliberately does *not* reuse a JSONB blob for the value itself, since the spec was explicit that arbitrary JSON should not be the primary value model here.
