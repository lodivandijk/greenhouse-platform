# Greenhouse Platform — UI v1 Implementation Specification

## 1. Objective

Implement a simple, stylish, responsive, read-only greenhouse dashboard for the existing Greenhouse Platform.

The dashboard must display:

* current digital twin state
* latest environmental observations
* active assessments
* device connectivity
* data freshness
* API connection status

The UI must consume the existing:

`GET /api/v1/state`

endpoint.

The UI must be served by the existing Spring Boot application and included in the existing deployable JAR.

This is a presentation-only release.

The UI must not:

* make environmental assessments
* duplicate assessment rules
* modify platform state
* control devices
* expose configuration editing
* introduce a separate frontend service

---

## 2. Architectural principles

The existing domain separation must be preserved.

```
Digital Twin
    Facts and current state
Assessment Engine
    Interpretation of those facts
UI
    Presentation of facts and assessments
```

The browser must not introduce its own domain rules.

For example, the UI must not contain logic such as:

```
if (humidityPercent > 65) {
    showHumidityWarning();
}
```

Instead, environmental warnings must be rendered only from assessments returned by:

`GET /api/v1/state`

The UI may contain presentation logic such as:

* formatting dates
* formatting units
* sorting assessments
* mapping severity to CSS classes
* determining whether API data is stale based on timestamps already supplied by the backend
* rendering unavailable values safely

---

## 3. Scope

### 3.1 Included in UI v1

Implement one dashboard page containing:

1. page header
2. greenhouse connectivity status
3. current environmental readings
4. active assessment cards
5. system and device status
6. automatic refresh
7. manual refresh
8. loading state
9. empty state
10. API failure state
11. stale-data state
12. responsive iPad, desktop and mobile layouts

### 3.2 Excluded from UI v1

Do not implement:

* historical charts
* database queries from the browser
* actuator controls
* irrigation controls
* assessment acknowledgement
* assessment history
* rule editing
* threshold configuration
* authentication
* user accounts
* notifications
* multiple pages
* client-side routing
* React
* Vue
* Angular
* Node.js runtime
* npm dependencies
* CSS frameworks
* component libraries
* WebSockets
* server-sent events
* a separate frontend deployment

---

## 4. Technology choice

Use:

* semantic HTML5
* modern CSS
* vanilla JavaScript
* browser fetch
* Spring Boot static-resource hosting

Do not add a frontend framework.

Do not add a separate frontend build step.

Do not add third-party JavaScript dependencies unless there is a strong technical reason. The default expectation is zero frontend dependencies.

---

## 5. Proposed file structure

Add the following files under the Spring Boot backend module:

```
backend/
└── src/
    └── main/
        └── resources/
            └── static/
                ├── index.html
                ├── app.js
                ├── styles.css
                ├── manifest.webmanifest
                └── icons/
                    └── greenhouse.svg
```

If the repository uses a different backend module path, place the files under the active Spring Boot module's:

`src/main/resources/static/`

Do not move existing backend source files unless necessary.

---

## 6. Required preliminary step: inspect the real API contract

Before implementing the data mapping, inspect the existing response from:

`GET /api/v1/state`

Do not invent field names.

Capture at least one real or test-generated JSON example representing:

* normal connected state
* connected state with active assessments
* offline state
* unknown or incomplete state, where supported

Document the actual fields used for:

* greenhouse ID
* greenhouse name, if present
* twin status
* zone ID
* zone name
* device ID
* device status
* temperature
* humidity
* pressure
* latest observation timestamp
* data freshness
* active assessments
* assessment code or type
* severity
* message
* confidence
* assessment start time
* evidence
* threshold
* observed value

If the existing API response does not contain every display field described in this specification, do not expand the backend domain purely for decorative UI content.

Instead:

* display only authoritative fields currently returned
* omit unavailable optional fields
* label missing data clearly
* document any recommended future API additions separately

Do not duplicate backend calculations in the frontend to compensate for missing fields.

---

## 7. Primary screen

The application should contain one page:

**Greenhouse Overview**

The page should be usable immediately without navigation.

Suggested information hierarchy:

```
Header
├── greenhouse name
├── connectivity status
├── last refreshed time
└── refresh button
Current environment
├── temperature
├── humidity
└── pressure
Active assessments
├── assessment card
├── assessment card
└── healthy empty state
System status
├── device
├── latest observation
├── data age
├── active assessment count
└── API status
```

---

## 8. Page layout

### 8.1 Header

Display:

* product label: Greenhouse
* greenhouse name or ID
* connectivity status
* relative last-updated value
* manual refresh button

Example:

```
GREENHOUSE
Saltwood Greenhouse                         Online
Updated 12 seconds ago                     Refresh
```

If no human-readable greenhouse name exists, display the configured greenhouse ID.

The connectivity status must reflect the digital twin status returned by the backend.

Do not infer environmental health from twin connectivity.

Supported visual labels should map to actual backend values.

Expected conceptual mapping:

```
NORMAL   → Online
OFFLINE  → Offline
UNKNOWN  → Unknown
```

Use the actual enum values present in the codebase.

### 8.2 Current environment section

Display a card for each currently supported environmental reading.

Initial expected cards:

* temperature
* humidity
* pressure

Each card must contain:

* metric label
* formatted value
* unit
* source freshness or availability state where useful

Examples:

```
Temperature
24.6 °C
Humidity
68 %
Pressure
1012 hPa
```

Do not display environmental classifications such as:

```
Normal
High
Low
Stable
```

unless those classifications are explicitly returned by the backend assessment engine.

A reading card is a display of facts, not an assessment.

**Null handling**

If a reading is null or absent, display:

`Not available`

Do not display zero as a substitute.

Examples of values that must not be fabricated:

```
0 °C
0 %
0 hPa
```

### 8.3 Active assessments section

This section is the most prominent part of the page after current readings.

Each assessment should render as a separate card.

Use the real assessment response fields. Where available, show:

* assessment title or code
* severity
* human-readable message
* affected greenhouse, zone or device
* observed value
* relevant threshold
* confidence
* active-since timestamp
* evidence summary

A possible card structure:

```
WARNING
High humidity
Humidity is currently above the configured operating threshold.
Zone: Main greenhouse
Observed: 72 %
Threshold: 65 %
Confidence: 94 %
Active since: 18:42
```

Only show optional rows when corresponding data exists.

Do not show empty labels such as:

```
Confidence:
Threshold:
Evidence:
```

**Assessment ordering**

Render assessments in this priority:

1. critical
2. warning
3. advisory
4. informational

Within the same severity, place the oldest active assessment first, unless the current backend contract already defines a meaningful order.

Keep the sorting implementation isolated in one presentation function.

**No active assessments**

When there are no active assessments, render a deliberate healthy state.

Example:

```
No active assessments
Environmental conditions are currently within the configured operating thresholds.
```

Do not show an empty section.

Do not state that conditions are healthy if the platform is offline or data is unavailable.

For offline or unknown states with no assessments, use wording such as:

```
No active assessments are available.
Current environmental conditions cannot be confirmed because recent sensor data is unavailable.
```

### 8.4 System status section

Display a compact system-status panel.

Where available, include:

* device ID
* device connectivity state
* latest observation timestamp
* age of latest observation
* number of active assessments
* API connection state
* greenhouse ID
* zone ID

Example:

```
System status
Device                  esp32-01
Device status           Online
Latest observation      19:12:04
Data age                12 seconds
Active assessments      1
API status              Connected
```

Do not expose internal database IDs unless they are operationally useful.

---

## 9. Visual style

Create a restrained, modern greenhouse aesthetic.

### 9.1 General style

Use:

* light neutral page background
* white or slightly tinted cards
* dark charcoal text
* muted green as the principal accent
* rounded corners
* subtle borders
* restrained shadows
* generous spacing
* large, readable environmental values
* clear section headings

Avoid:

* bright neon colours
* heavy gradients
* decorative animations
* dense admin-dashboard styling
* large amounts of iconography
* glassmorphism
* excessive shadows
* overly rounded pill controls
* visual clutter

### 9.2 Typography

Use a system font stack.

Example:

```
font-family:
    Inter,
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
```

Do not load remote fonts.

Recommended hierarchy:

* main environmental values: large and bold
* page title: prominent but restrained
* section titles: medium weight
* labels: small and muted
* assessment messages: normal body text

### 9.3 Assessment severity presentation

Colour must not be the only severity indicator.

Each assessment must include a written severity label.

Suggested conceptual styling:

```
INFORMATIONAL → blue-grey
ADVISORY      → amber
WARNING       → orange
CRITICAL      → red
```

Use actual backend severity values.

Each assessment card should include:

* text label
* severity-specific border or accent
* optional simple icon
* accessible contrast

### 9.4 Connectivity presentation

Use:

* green indicator for online
* red or muted red for offline
* grey for unknown

Include text as well as colour.

Examples:

```
● Online
● Offline
● Unknown
```

---

## 10. Responsive design

The UI must be designed for iPad use first, while remaining usable on phones and desktop browsers.

### 10.1 Desktop and iPad landscape

Use:

* centred content container
* sensible maximum width
* three environmental cards in one row
* assessments below
* system status in a full-width card or secondary column

Suggested maximum page width:

`max-width: 1200px;`

### 10.2 iPad portrait

Use:

* two or three environmental cards per row depending on available width
* full-width assessment cards
* no horizontal scrolling

### 10.3 Mobile

Use:

* one or two environmental cards per row
* vertically stacked assessment details
* large touch targets
* readable text without zooming
* no horizontal scrolling

Minimum touch target:

`44 × 44 CSS pixels`

### 10.4 Breakpoints

Use only the breakpoints needed by the layout.

A reasonable approach:

```
mobile: below 640px
tablet: 640px–1024px
desktop: above 1024px
```

Do not over-engineer breakpoint handling.

---

## 11. Data fetching

Use:

`fetch("/api/v1/state")`

The browser must use the same origin as the Spring Boot application.

Do not hard-code:

* hostnames
* IP addresses
* ports
* Tailscale names
* environment-specific URLs

### 11.1 Fetch timing

On page load:

1. show loading state
2. fetch `/api/v1/state` immediately
3. render the response
4. begin periodic polling

Poll every:

`20 seconds`

Store the polling interval in one named constant.

Example:

```
const REFRESH_INTERVAL_MS = 20_000;
```

### 11.2 Tab visibility

Use the Page Visibility API.

When the tab becomes hidden:

* stop or suspend periodic polling

When the tab becomes visible:

* immediately refresh
* restart polling

This avoids unnecessary network calls while preserving freshness when the user returns.

### 11.3 Manual refresh

Provide a refresh button.

During refresh:

* disable the button
* indicate loading
* prevent overlapping fetches

After completion:

* restore the button
* update the displayed refresh time

---

## 12. Error handling

The UI must clearly distinguish:

1. greenhouse device offline
2. API unreachable
3. partial data
4. stale data
5. unknown twin state

These are not equivalent.

### 12.1 API unreachable

If `/api/v1/state` cannot be reached:

* show an API connection warning
* retain the last successfully rendered state
* visually mark retained values as potentially stale
* show when the last successful refresh occurred
* do not replace readings with zero
* do not imply that the greenhouse device itself is offline

Example:

```
Unable to refresh dashboard
The Greenhouse API could not be reached.
The values below are from the last successful update 3 minutes ago.
```

If there has never been a successful response, show a dedicated full-page error state.

Example:

```
Greenhouse dashboard unavailable
The application could not retrieve the current greenhouse state.
Check that the Greenhouse Platform service is running and reachable.
```

Do not expose raw stack traces.

A concise technical message may be logged to the browser console.

### 12.2 Device offline

If the twin reports offline:

* show Offline prominently
* retain last-known observations if the API provides them
* mark those observations as stale
* show their actual age
* continue rendering active assessments returned by the backend

Do not treat an offline device as an API failure.

### 12.3 Unknown state

If the twin status is unknown:

* show Unknown
* use neutral styling
* explain that current device state cannot be determined
* render available readings without implying they are current

### 12.4 Partial data

The UI must tolerate missing:

* temperature
* humidity
* pressure
* device
* zone
* assessment confidence
* threshold
* evidence
* timestamps

Optional content must be omitted or replaced with a clear `Not available` label.

The page must not throw a JavaScript error because an optional nested object is absent.

---

## 13. Stale-data behaviour

Prefer explicit freshness data returned by the backend.

If the backend provides:

* a freshness status
* latest observation age
* stale flag
* connectivity status

use those fields directly.

Do not recreate backend freshness thresholds in JavaScript.

If the backend provides only a latest-observation timestamp, the frontend may display relative age such as:

```
12 seconds ago
3 minutes ago
1 hour ago
```

However, it must not classify the reading as stale using its own threshold unless that threshold is already part of the public API contract.

---

## 14. Date and time formatting

Use the browser locale by default.

Expected locale:

`en-GB`

Examples:

```
19:12:04
27 July 2026
12 seconds ago
3 minutes ago
```

Use semantic `<time>` elements where practical.

Each relative time should preserve the actual timestamp in:

* the `datetime` attribute
* a tooltip or accessible label

Do not rely only on relative time.

---

## 15. Accessibility

Meet basic accessible-dashboard standards.

Required:

* semantic headings
* sufficient colour contrast
* keyboard-accessible refresh button
* visible focus states
* text labels alongside status colours
* `aria-live` for refresh and connection status where appropriate
* no information communicated only by colour
* meaningful page title
* decorative SVGs marked appropriately
* readable font sizes
* reduced-motion-safe behaviour

Use:

`<html lang="en-GB">`

The refresh button must have an accessible name.

Assessment severity must be readable by screen readers.

---

## 16. HTML structure

Use semantic structure similar to:

```html
<body>
  <header class="app-header">
    ...
  </header>
  <main class="dashboard">
    <section aria-labelledby="environment-heading">
      ...
    </section>
    <section aria-labelledby="assessments-heading">
      ...
    </section>
    <section aria-labelledby="system-heading">
      ...
    </section>
  </main>
  <footer>
    ...
  </footer>
</body>
```

Do not render the entire page through `innerHTML`.

The static layout should be defined in `index.html`.

Use JavaScript to update specific DOM nodes and create repeated assessment cards safely.

Prefer:

* `document.createElement`
* `textContent`
* dedicated render functions

Avoid injecting untrusted API strings through raw `innerHTML`.

---

## 17. JavaScript structure

Keep `app.js` small and organised.

Suggested structure:

```js
const CONFIG = {
    stateEndpoint: "/api/v1/state",
    refreshIntervalMs: 20_000
};
const dashboardState = {
    lastSuccessfulResponse: null,
    lastSuccessfulRefreshAt: null,
    refreshTimerId: null,
    requestInFlight: false
};
async function fetchState() {}
function renderDashboard(state) {}
function renderHeader(state) {}
function renderEnvironment(state) {}
function renderAssessments(state) {}
function renderSystemStatus(state) {}
function renderLoadingState() {}
function renderApiError(error) {}
function renderNoAssessments(state) {}
function formatRelativeTime(timestamp) {}
function formatAbsoluteTime(timestamp) {}
function formatMetric(value, unit, precision) {}
function mapTwinStatus(status) {}
function mapAssessmentSeverity(severity) {}
function startPolling() {}
function stopPolling() {}
function handleVisibilityChange() {}
```

The exact API mapping should be isolated.

For example:

```js
function mapApiState(apiResponse) {
    return {
        greenhouse: ...,
        environment: ...,
        assessments: ...,
        devices: ...
    };
}
```

This mapping layer must not add domain interpretation.

It should only adapt API field names into a stable presentation model.

---

## 18. Presentation model

Create a lightweight UI presentation model based on the actual API response.

Conceptually:

```json
{
    "greenhouse": {
        "id": "greenhouse-01",
        "name": "Saltwood Greenhouse",
        "status": "NORMAL",
        "observedAt": "2026-07-27T18:12:04Z"
    },
    "environment": {
        "temperatureCelsius": 24.6,
        "humidityPercent": 68.0,
        "pressureHpa": 1012.0
    },
    "assessments": [
        {
            "code": "HIGH_HUMIDITY",
            "title": "High humidity",
            "severity": "WARNING",
            "message": "Humidity is above the configured threshold.",
            "zoneId": "main-zone",
            "observedValue": 72,
            "thresholdValue": 65,
            "unit": "%",
            "confidence": 0.94,
            "activeSince": "2026-07-27T17:54:00Z"
        }
    ],
    "system": {
        "deviceId": "esp32-01",
        "deviceStatus": "ONLINE",
        "latestObservationAt": "2026-07-27T18:12:04Z"
    }
}
```

This example is conceptual only.

Use the actual backend contract.

Do not modify the backend simply to match this example.

---

## 19. CSS structure

Organise `styles.css` using clear sections:

```
/* Design tokens */
/* Base and reset */
/* Layout */
/* Header */
/* Status indicators */
/* Metric cards */
/* Assessment cards */
/* System status */
/* Empty and error states */
/* Buttons */
/* Responsive rules */
/* Accessibility and reduced motion */
```

Use CSS custom properties for design tokens.

Example:

```css
:root {
    --background: #f4f6f2;
    --surface: #ffffff;
    --surface-muted: #eef2eb;
    --text-primary: #1f2923;
    --text-secondary: #667068;
    --border: #dce3dc;
    --accent: #416b50;
    --accent-dark: #294a35;
    --radius-small: 10px;
    --radius-medium: 16px;
    --shadow-soft: 0 8px 24px rgba(31, 41, 35, 0.07);
}
```

Exact colours may vary, but keep the overall aesthetic muted and readable.

Do not use inline styles except where unavoidable for dynamic values.

---

## 20. Icons

Use simple inline SVG icons or one local SVG asset.

Do not use an external icon service.

Possible icons:

* greenhouse or leaf for application identity
* thermometer
* droplet
* pressure gauge
* warning triangle
* check circle
* connectivity indicator
* refresh arrow

Icons must remain secondary to written labels.

---

## 21. Browser support

Target current versions of:

* Safari on iPadOS
* Safari on macOS
* Chrome on macOS
* Chrome on Android
* Edge on Windows

Do not depend on experimental browser features.

---

## 22. Spring Boot integration

The UI must be served at:

`GET /`

Spring Boot should serve:

`src/main/resources/static/index.html`

The existing API must remain available at:

`GET /api/v1/state`

Do not introduce a controller for `/` unless static resource handling does not work in the current application.

Do not change existing API paths.

Do not add CORS configuration because the UI and API are same-origin.

---

## 23. Web application manifest

Add a minimal:

`manifest.webmanifest`

This should support adding the dashboard to the iPad home screen later.

Suggested fields:

```json
{
  "name": "Greenhouse Platform",
  "short_name": "Greenhouse",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#f4f6f2",
  "theme_color": "#416b50",
  "icons": []
}
```

Do not implement a service worker or offline caching in v1.

Link the manifest from `index.html`.

Include appropriate mobile web application meta tags where useful.

---

## 24. Loading behaviour

On first load:

* show the page shell immediately
* show skeleton or restrained loading placeholders
* display `Loading current greenhouse state`
* fetch the state
* replace placeholders when data arrives

Avoid large animated spinners.

Loading indicators should respect:

`@media (prefers-reduced-motion: reduce)`

---

## 25. Footer

Include a subtle footer containing available platform metadata.

Possible fields:

* Greenhouse Platform
* application version, if already exposed safely
* API connected or disconnected
* current dashboard refresh interval

Do not add a fake version number.

If build version is not available to the UI, omit it.

---

## 26. Tests

Add proportionate tests.

### 26.1 Backend/static-resource test

Add a Spring Boot integration test confirming:

`GET /`

returns:

```
HTTP 200
Content-Type: text/html
```

Also confirm the returned HTML contains a stable identifier such as:

`data-app="greenhouse-dashboard"`

Do not alter existing `/api/v1/state` tests except where necessary.

### 26.2 JavaScript tests

Do not introduce a full JavaScript test framework solely for this release.

Instead, keep data mapping and formatting functions simple and independently inspectable.

If the repository already has a JavaScript test setup, add tests for:

* null metric formatting
* relative time formatting
* severity mapping
* status mapping
* assessment ordering

### 26.3 Manual test fixtures

Provide documented example API responses for manual validation.

At minimum test:

1. online with no assessments
2. online with one assessment
3. online with multiple severities
4. offline with last-known observations
5. unknown state
6. missing temperature
7. missing humidity
8. missing pressure
9. API returns non-200 response
10. API request times out or fails
11. malformed optional assessment data
12. iPad portrait
13. iPad landscape
14. mobile width
15. desktop width

---

## 27. Logging

Frontend console logging should be minimal.

Permitted:

`Failed to retrieve greenhouse state`

Do not log the full API response on every polling cycle.

Do not log secrets or environment configuration.

---

## 28. Security considerations

The UI is read-only.

Still ensure:

* API text is rendered with `textContent`
* no raw API-provided HTML is inserted
* no external scripts are loaded
* no secrets are embedded
* no hard-coded credentials exist
* no database connection information is exposed
* no internal exception details are displayed
* same-origin API calls are used

Do not add authentication in this release.

Tailscale and network-level access remain outside the scope of this UI implementation.

---

## 29. Documentation updates

Add a short UI section to the existing project documentation.

Include:

* purpose of UI v1
* route: `/`
* API dependency: `/api/v1/state`
* file locations
* refresh interval
* deployment method
* known limitations
* manual verification instructions

Suggested document:

`docs/ui/ui-v1.md`

Also update the main README with a short link or section.

---

## 30. Deployment behaviour

The static resources must be bundled into the normal Spring Boot JAR.

The existing deployment process should remain:

```
build application
copy JAR to Raspberry Pi
restart Spring Boot service
```

No additional process should be required on the Pi.

After deployment, verify:

`http://<raspberry-pi-host>:<existing-port>/`

and:

`http://<raspberry-pi-host>:<existing-port>/api/v1/state`

The exact host and port must come from the current deployment configuration.

---

## 31. Acceptance criteria

UI v1 is complete when all of the following are true.

**Functional**

* `GET /` serves the dashboard.
* The dashboard calls `GET /api/v1/state`.
* The current twin connectivity state is visible.
* Temperature is displayed when available.
* Humidity is displayed when available.
* Pressure is displayed when available.
* Null readings display `Not available`.
* Active assessments are displayed.
* Assessment severity is displayed in text.
* No-assessment state is clearly rendered.
* Device and data freshness information is displayed where available.
* The dashboard refreshes automatically every 20 seconds.
* The dashboard supports manual refresh.
* Polling pauses when the page is hidden.
* An immediate refresh occurs when the page becomes visible.
* API errors do not erase the last successful data.
* API failure is distinguished from device offline.
* The page does not implement environmental thresholds or assessment rules.
* The page contains no device-control functionality.

**Visual**

* The interface has a clean greenhouse-oriented visual style.
* The interface works in iPad landscape.
* The interface works in iPad portrait.
* The interface works at mobile width.
* The interface works at desktop width.
* No horizontal scrolling occurs at supported widths.
* Status is not communicated only through colour.
* Assessment cards are clearly prioritised.
* The page remains readable in bright conditions.

**Technical**

* No frontend framework has been introduced.
* No Node.js runtime is required.
* No external frontend dependencies are required.
* Static files are packaged in the Spring Boot JAR.
* Existing APIs remain unchanged.
* Existing backend tests continue to pass.
* A test confirms that `/` serves the dashboard.
* API-provided strings are rendered safely.
* No hard-coded hostnames or IP addresses are present.
* No CORS configuration is required.
* Documentation has been updated.

---

## 32. Suggested implementation sequence

Implement in this order.

**Step 1 — Inspect contract**

* inspect `/api/v1/state`
* identify exact field names
* record representative JSON
* identify nullable fields
* identify enums

**Step 2 — Create static page shell**

* add `index.html`
* add page sections
* add accessible labels
* add loading placeholders

**Step 3 — Apply styling**

* add design tokens
* build responsive layout
* style metric cards
* style assessment cards
* style status and error states

**Step 4 — Implement API mapping**

* fetch `/api/v1/state`
* map actual API response to the presentation model
* render current readings
* render connectivity
* render system details

**Step 5 — Implement assessments**

* map real assessment fields
* order by severity
* render optional details safely
* render healthy and unavailable empty states

**Step 6 — Implement refresh behaviour**

* immediate load
* 20-second polling
* manual refresh
* page visibility handling
* prevent overlapping requests

**Step 7 — Implement failures and partial states**

* first-load failure
* refresh failure
* retained last-known state
* missing metrics
* offline device
* unknown device

**Step 8 — Add tests**

* static route integration test
* existing backend regression tests
* manual scenarios

**Step 9 — Documentation**

* add `docs/ui/ui-v1.md`
* update README
* document deployment and verification

**Step 10 — Deploy and verify**

* build JAR
* deploy using current Pi process
* verify locally
* verify through Tailscale
* verify on iPad portrait and landscape

---

## 33. Definition of done

The task is done when the user can open the Raspberry Pi-hosted Greenhouse Platform in Safari on an iPad and, within a few seconds, clearly understand:

* whether the platform is online
* the latest temperature
* the latest humidity
* the latest pressure
* whether any assessments are active
* how serious those assessments are
* which device or zone is affected, where available
* how recent the data is
* whether the dashboard is currently connected to the API

The final implementation must remain read-only and must preserve the separation between twin facts, assessment-engine interpretation and UI presentation.

---

## 34. Deliverables

Provide:

1. implemented UI files
2. any required Spring Boot static-resource integration
3. integration test for `/`
4. `docs/ui/ui-v1.md`
5. README update
6. representative screenshots or a concise visual verification note
7. summary of changed files
8. confirmation that backend tests pass
9. confirmation that the deployable JAR includes the UI
10. confirmation that no assessment logic exists in the browser
