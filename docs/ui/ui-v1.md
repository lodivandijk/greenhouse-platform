# Greenhouse Platform — UI v1

## Purpose

A single-page, read-only dashboard showing the current greenhouse state: connectivity, environmental readings, active assessments, and device/system status. It is a presentation layer only — it contains no environmental thresholds or assessment logic of its own. All interpretation (severity, warnings, "too hot" etc.) comes from the Assessment Engine via the API.

See [assessment-engine-v1-spec.md](../architecture/assessment-engine-v1-spec.md) and [ui-v1-spec.md](../architecture/ui-v1-spec.md) for the full specs this was built from.

## Route

`GET /` — served by Spring Boot's default static-resource handling from `src/main/resources/static/index.html`. No custom controller was needed.

## API dependency

The dashboard fetches `GET /api/v1/state` (same-origin, no CORS configuration needed) on load and every 20 seconds thereafter. It does not call any other endpoint and never writes to the API.

## File locations

All under `backend/src/main/resources/static/`:

- `index.html` — static page shell (header, environment section, assessments section, system status, footer). All API-driven content is populated by JS, not baked into the HTML.
- `styles.css` — design tokens, layout, and responsive rules (mobile / tablet / desktop breakpoints at 640px / 1024px).
- `app.js` — fetch/poll/render logic. No dependencies; vanilla `fetch`, `document.createElement`, `textContent`.
- `manifest.webmanifest` — minimal web app manifest for adding the dashboard to an iPad home screen. No service worker.
- `icons/greenhouse.svg` — the only icon asset; used as favicon, apple-touch-icon, and manifest icon.

## Refresh interval

20 seconds (`CONFIG.refreshIntervalMs` in `app.js`), plus manual refresh via the header button. Polling pauses while the browser tab is hidden (Page Visibility API) and triggers an immediate refresh when the tab becomes visible again.

## Deployment

No change to the deployment process. Static resources are packaged into the same Spring Boot JAR by the normal Gradle build (`bootJar`), so `./scripts/deploy.sh` ships the dashboard automatically — no extra step on the Pi.

## Known limitations

- **Single primary zone/device**: the dashboard renders `zones[0]` and its first device from the twin response. The current deployment has exactly one zone (`zone-main`) and one device, so this is not a visible limitation today, but the UI does not yet render multiple zones or devices if the greenhouse topology grows. Revisit if a second zone is added.
- **No app version in the footer**: `GET /actuator/info` is exposed but currently returns `{}` (the environment info contributor isn't populated), so per the spec's "do not add a fake version number" rule, the footer omits a version string entirely.
- **Assessment "evidence" is rendered generically**: each assessment rule's `evidence` map has different, rule-specific keys (e.g. `actualTemperatureCelsius` vs `lastSeenAt`). The UI does not know what these mean — it humanizes the key name (`actualTemperatureCelsius` → "Actual Temperature Celsius") and shows the raw value. The assessment `message` field (a complete human-readable sentence produced by the rule) is the primary content of each card; evidence is a supplementary detail list.
- **No live-ticking clock**: relative times ("12 seconds ago") update on each poll/render, not continuously between polls.

## Manual verification instructions

1. Build and run the jar locally (`SPRING_DATASOURCE_PASSWORD=... java -jar backend/build/libs/greenhouse-platform.jar`).
2. Open `http://localhost:8080/` in a browser.
3. Confirm the header shows the greenhouse name, an "Online" connectivity indicator, and the current readings.
4. POST an out-of-range observation to `/api/v1/observations` and wait for the next scheduler cycle (up to 1 minute); confirm an assessment card appears with severity, message, and details.
5. Stop the backend process; confirm the dashboard shows the "Unable to refresh dashboard" banner and keeps the last-known readings visible (marked as potentially stale), rather than erasing them.
6. Reload the page with the backend stopped; confirm the full-page "Greenhouse dashboard unavailable" error is shown instead of a broken layout.
7. Resize the browser (or use responsive device mode) to iPad portrait, iPad landscape, and mobile widths; confirm no horizontal scrolling and that metric/assessment cards reflow sensibly.
8. Switch browser tabs away and back; confirm polling pauses while hidden (no network activity in dev tools) and an immediate refresh fires on return.
