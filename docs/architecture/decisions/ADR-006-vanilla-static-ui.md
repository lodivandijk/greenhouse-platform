# ADR-006: Read-Only UI Served as Static Resources, No Frontend Framework

**Status:** Accepted
**Date:** 2026-07-28

> This ADR postdates the architecture paradigm's own worked example list (ADR-001 through ADR-005), added when bootstrapping ADR history for this project. It records the first real UI-layer architectural decision made under the new "facts / interpretation / presentation" boundary the paradigm formalises.

## Context

`GET /api/v1/state` (ADR-005) made a complete operational picture available over HTTP, but there was still no human-facing way to see it short of calling the API directly. The platform needed a UI, and per the architecture paradigm's domain-separation principle, that UI needed to be presentation-only: it must not introduce a fourth place where environmental thresholds or severity judgements get decided, alongside the twin (facts) and assessment engine (interpretation).

## Decision

Build a single-page, read-only dashboard as static resources (`backend/src/main/resources/static/`) served directly by Spring Boot's existing static-resource handling — no new controller for `GET /`, no separate frontend service, no build step, no framework (React/Vue/Angular explicitly rejected), no third-party JavaScript dependencies.

- `index.html` / `styles.css` / `app.js` (vanilla, IIFE-wrapped) / `manifest.webmanifest`, packaged into the same deployable JAR the backend already ships via the existing `deploy.sh` process — no new deployment step.
- The only data dependency is `GET /api/v1/state`, polled every 20 seconds with Page Visibility API-aware pause/resume, plus manual refresh.
- A `mapApiState()` function isolates all API-field-to-presentation-model translation in one place. It performs *only* renaming/reshaping (e.g. picking `zones[0]` as the primary zone) — it must never encode a threshold or a severity decision. Severity, "too hot", "device offline" etc. all originate from the assessment engine's response fields; the UI only formats, sorts, and labels them.
- Where the API contract didn't provide a field the UI conceptually wanted (a `confidence` score, a generic `observedValue`/`threshold` pair, an assessment `title`), the UI does not fabricate one — missing fields are omitted or handled generically (e.g. each rule's differently-shaped `evidence` map is rendered as a humanized key/value list rather than the UI assuming a canonical shape only some rules actually have).

## Consequences

- Kept the "facts ≠ interpretation" boundary intact through a fourth layer: Device/Ingestion → Observation → Twin → Assessment → **Application State → UI**, exactly matching the architecture paradigm's §5 domain chain.
- Zero new runtime dependencies, zero new infrastructure, zero change to the deployment process — the lowest-risk way to add a UI to this specific platform at this stage.
- The UI is necessarily coupled to the exact shape of `/api/v1/state` (rule-specific `evidence` keys, `AssessmentCode` enum values needing a friendly-label lookup in JS). Future assessment codes will render with a generic fallback label rather than breaking, but won't get a hand-tuned friendly name until the UI is updated.
- No historical charts, no acknowledgement/suppression, no authentication — deliberately excluded as out of scope for a v1 presentation layer, consistent with the platform still being purely observational (architecture paradigm §4).

## Related / superseded decisions

Depends on ADR-005. Sits at the end of the current implemented chain described in `CURRENT_ARCHITECTURE.md`; the paradigm's "NEXT"/"LATER" stages (objective-driven reasoning, decisions, actions) would introduce write paths this ADR's read-only UI does not anticipate and would need its own future ADR.
