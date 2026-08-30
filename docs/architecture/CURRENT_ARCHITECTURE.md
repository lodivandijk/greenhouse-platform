# Current Architecture

This document describes **only implemented, deployed reality**. It is the authoritative description of what the system looks like today — not what is planned or being considered. See `ARCHITECTURE_PARADIGM.md` for how this document relates to the ADRs in `decisions/`, and for the longer-term direction this system is expected to grow toward.

For exact implementation details, the source code and tests remain authoritative over this document.

---

## 1. Purpose

A monitoring platform for a home greenhouse: an ESP32 sensor node reports environmental readings, soil moisture, and connectivity heartbeats to a Spring Boot backend, which persists them, derives current state, evaluates that state against configured operating limits *and against each crop's own preferred conditions*, and presents the result on a read-only dashboard. The backend also persists crop-level knowledge (what's being grown, goals, harvests, manual observations, work performed) and exposes both machine state and crop knowledge to AI clients over MCP.

It now also runs a **human-in-the-loop care cycle**: a detected condition can become a proposed decision, an approved command, a recorded execution, and an evidence-based outcome — all append-only, all resumable by a fresh agent session. The system remains **observational, not autonomous**: there is no path from software into the physical greenhouse, every command targets a human, and no decision becomes a command without an explicit human approval.

```
SENSE → STORE → MODEL → ASSESS → DECIDE → COMMAND → EXECUTE → OUTCOME → (next observation)
                                    ↑ human approval required at every gate
```

## 2. Deployed components

```
ESP32-PICO-KIT + BME280            Raspberry Pi (greenhouse-pi, Tailscale 100.77.67.92)
(firmware/GreenhouseESP32/)   ────► ┌─────────────────────────────────────────────┐
  home Wi-Fi, HTTP POST             │ systemd unit "greenhouse"                    │
                                     │   java -jar greenhouse-platform.jar          │
                                     │   (Spring Boot 4.1.0, Java 21+, port 8080)   │
                                     │                                               │
                                     │ PostgreSQL 17 (127.0.0.1:5432, not exposed)  │
                                     └─────────────────────────────────────────────┘
                                              ▲
                                              │ Tailscale (WireGuard mesh VPN)
                                              │ MagicDNS suffix: tail89e44d.ts.net
                                     MacBook (development + deploy.sh)
```

- **Firmware** (`firmware/GreenhouseESP32/`): Arduino sketch. Connects to home Wi-Fi, POSTs a heartbeat on its own timer, and POSTs BME280 readings (temperature/humidity/pressure) plus raw soil-moisture ADC readings together in one observation payload on another timer, retries/logs failures. Symlinked into the Arduino IDE's sketchbook for local development. Accepts password-protected over-the-air updates on the home Wi-Fi network (`OtaService`/`ArduinoOTA`) once running a build that includes it — see ADR-019; the very first such build still needs a USB flash.
- **Backend** (`backend/`): single Spring Boot application, single deployable JAR (`greenhouse-platform.jar`), containing both the REST API and the static UI. No microservices, no separate frontend process.
- **Database**: PostgreSQL, one instance on the Pi (production) and one on the development Mac, kept schema-identical via the same Flyway migrations. Never exposed outside `localhost` — the backend is the only client, even over Tailscale.
- **Network**: Tailscale connects the Mac and the Pi over an encrypted private mesh. No SSH, PostgreSQL, or application port is exposed to the public internet. `ssh greenhouse-pi` works via a `~/.ssh/config` alias to the Tailscale IP (MagicDNS is not wired into this Mac's OS resolver because of the CLI-only Homebrew Tailscale install, so plain hostname lookups from `curl`/`ping` on this Mac specifically don't resolve — see `docs/operations/remote-access.md`).
- **Deployment**: `scripts/deploy.sh` — refuses to run on a dirty git tree, builds and tests locally, `scp`s the jar to `/opt/greenhouse/`, then over SSH backs up the previous jar, restarts the systemd unit, and polls `/actuator/health` until healthy.

## 3. Domain boundaries (backend packages)

```
com.greenhouse.heartbeat     Device connectivity heartbeats (ingestion)
com.greenhouse.device        Device registry / connectivity status (persisted)
com.greenhouse.observation   Environmental readings + soil moisture telemetry (persisted, append-only)
com.greenhouse.twin          Digital Twin — current factual state, assembled per request
com.greenhouse.assessment    Assessment Engine — interpretation, persisted lifecycle
com.greenhouse.evaluation    Scheduler + coordinator orchestrating twin → assessment
com.greenhouse.state         Composed read model (twin + active assessments)
com.greenhouse.crop          Crop, Harvest, CropObservation — biological/semantic evidence (persisted)
com.greenhouse.goal          Goal — user intent for a crop, not executable control (persisted)
com.greenhouse.action        Action — agricultural work performed on a crop, not machine control (persisted)
com.greenhouse.careloop      Care loop — Decision, Command, Execution, Outcome and scope (append-only)
com.greenhouse.briefing      Daily structured crop-status snapshot (immutable, versioned)
com.greenhouse.notification  Outbound notification — intent, policy, delivery (append-only projection)
com.greenhouse.mcp           MCP server + tools — the agent capability boundary
com.greenhouse.common        Cross-cutting (API exception handling, idempotency)
static/                      Read-only UI (served by Spring Boot's default static handling)
```

The governing principle across these boundaries is **facts ≠ interpretation** (see `ARCHITECTURE_PARADIGM.md` §5–§8, and ADR-003/ADR-004 for how this was arrived at):

```
Device / Ingestion  "What did the hardware send?"
        ↓
Observation          "What was measured?"
        ↓
Digital Twin          "What do we currently know to be true?"
        ↓
Assessment            "What do those facts mean?"
        ↓
Application State      "What does the application need to present?"
        ↓
UI                     "What does the user need to see?"
```

The Digital Twin (`com.greenhouse.twin`) contains **no** environmental judgement — no thresholds, no severity, no "too hot"/"too cold". It reports facts only: readings, device connectivity (`ONLINE`/`DELAYED`/`OFFLINE`/`UNKNOWN`), and data freshness (`CURRENT`/`DELAYED`/`STALE`/`UNKNOWN`), all derived from configured timing thresholds, not judgement about whether that timing is acceptable. `TwinStatus` (`NORMAL`/`OFFLINE`/`UNKNOWN`) is derived purely from device connectivity.

All environmental interpretation — limit breaches, staleness-as-a-problem, offline-as-a-problem — lives exclusively in `com.greenhouse.assessment`.

`com.greenhouse.crop`, `com.greenhouse.goal`, and `com.greenhouse.action` are a parallel, deliberately separate domain: biological/semantic evidence about specific crops (health, flowering, harvests, user-stated goals, work performed), reported by a human via MCP or REST, not derived from sensors. `com.greenhouse.crop.CropObservation` and `com.greenhouse.observation.ObservationStatus` are unrelated types that happen to share the word "observation" — see ADR-009 for why that overlap was accepted rather than renamed. `Action` (what was done — watering, feeding, pruning) is likewise kept distinct from `CropObservation` (what was observed) and `Harvest` (what was produced) — see ADR-017. `Goal` and `Action` each live in their own package rather than inside `com.greenhouse.crop`, since both are explicitly forward-facing: `Goal` toward a future objective/decision model, `Action` toward a future `Control` layer that does not exist yet.

## 4. Data flow

```
ESP32 ──POST /api/v1/heartbeats──► HeartbeatController ─► DeviceService ─► device table
ESP32 ──POST /api/v1/observations──► ObservationController ─► ObservationService ─► observation table

                                    (on request, or from the scheduler)
device + observation tables ──► TwinService.getCurrentTwin() ──► GreenhouseTwin (facts)
                                                                        │
                                                                        ▼
                                                    AssessmentService.assessAndReconcile()
                                                     (4 rules → findings → reconciler)
                                                                        │
                                                                        ▼
                                                              assessment table (lifecycle)

GreenhouseStateService.getCurrentState() = TwinService.getCurrentTwin() + AssessmentQueryService.getActiveAssessments()
        │
        ▼
GET /api/v1/state ──► static UI (app.js polls every 20s)
```

Two independent triggers read/write this pipeline:

- **Request-driven**: any `GET` to `/api/v1/twin`, `/api/v1/assessments`, or `/api/v1/state` assembles the twin fresh from currently-persisted data. Reads never write.
- **Scheduler-driven**: `GreenhouseEvaluationScheduler` (`@Scheduled`, default `fixedDelay=PT1M`, `initialDelay=PT10S`, configurable via `greenhouse.evaluation.*`, disable with `greenhouse.evaluation.enabled=false`) is the **only** path that writes to the `assessment` table. It builds a twin, evaluates all four assessment rules against it, and reconciles the result (raise / update / resolve). Guarded against overlapping runs and against a single exception killing future scheduled executions.

## 5. Persistence

PostgreSQL, 30 tables via Flyway migrations (`backend/src/main/resources/db/migration/`), `spring.jpa.hibernate.ddl-auto=validate` (schema changes only happen through a migration, never Hibernate auto-DDL):

- **`observation`** (V1) — append-only log. One row per accepted reading: `device_id`, `temperature_celsius`, `humidity_percent`, `pressure_hpa`, `received_at`. Rows are never updated or deleted by the ingestion path. No foreign key to `device` (ingestion must keep working even if a device record is temporarily missing).
- **`device`** (V2) — one mutable row per device, natural key (`device_id`, no surrogate id): `software_version`, `first_seen_at`, `last_seen_at`, `last_ip_address`, `last_signal_strength_dbm`, `last_uptime_seconds`, `heartbeat_count`, `enabled`, `updated_at`. `online` is never stored — always derived at read time from `last_seen_at`.
- **`assessment`** (V3) — lifecycle-tracked. `correlation_key`, `greenhouse_id`/`zone_id`/`device_id`, `scope_type`/`scope_id`, `code`, `severity`, `status`, `message`, `evidence_json` (JSONB), `rule_id`/`rule_version`, `first_detected_at`/`last_detected_at`/`last_evaluated_at`/`resolved_at`. A **partial unique index** — `UNIQUE (correlation_key) WHERE status = 'ACTIVE'` — enforces at most one active record per logical condition at the database level while still allowing historical recurrence.
- **`crop`** (V4) — one row per crop: `species`, `variety`, `location_id` (an unvalidated string, e.g. `planter-02`), `planted_at`, `ended_at`, `status` (`PLANNED`/`ESTABLISHING`/`PRODUCTIVE`/`DECLINING`/`ENDED`), `notes`. Surrogate `id`, unlike `device`.
- **`goal`** (V5) — `crop_id` (FK), `goal_type` (enum + `OTHER`), `description`, `status` (`ACTIVE`/`COMPLETED`/`CANCELLED`), `priority`, `source_instruction` (the user's own words), `metadata_json` (JSONB).
- **`harvest`** (V6) — `crop_id` (FK), `harvested_at`, `quantity` + `unit` (`GRAMS`/`KILOGRAMS`/`COUNT` — always paired, never a bare number), `notes`.
- **`crop_observation`** (V7) — `crop_id` (FK), `metric` (enum + `OTHER`), `value_type` (`NUMERIC`/`TEXT`/`BOOLEAN`) discriminating exactly one of `numeric_value`/`text_value`/`boolean_value`, `unit`, `source` (`HUMAN`/`AI_DERIVED`/`DERIVED`/`EXTERNAL`), `confidence`, `observed_at`, `notes`, `metadata_json` (JSONB).
- **`action`** (V8) — `crop_id` (FK), `type` (enum: `WATER`/`FEED`/`PRUNE`/`POLLINATE`/`MOVE`/`PLANT`/`OTHER`), `description`, `quantity` + `unit` (quantity requires unit, same "never a bare number" rule as `harvest`), `performed_at`, `performed_by` (`HUMAN`/`AGENT`/`AUTOMATION`/`SYSTEM`, defaults to `HUMAN`), `created_at`.
- **`soil_moisture_reading`** (V9) — append-only log, one row per physical probe per observation cycle: `device_id`, `sensor_id` (the probe's stable identity, e.g. `soil-01`), `raw_adc` (`INTEGER CHECK (raw_adc BETWEEN 0 AND 4095)`), `millivolts` (nullable, unpopulated until calibration work lands), `received_at`. No foreign key to `observation` or `crop` — see ADR-018 for why the two telemetry tables stay independent rather than parent/child. No `observed_at`: the firmware has no wall-clock time source yet, so only backend-assigned `received_at` is stored.

**Care-loop tables (V10–V26, ADR-021/ADR-022)** — the append-only half of the schema. Business records here are never updated in place; a correction is a new linked row.

- **`crop_monitoring_profile`** (V10) — versioned per-crop interpretation: preferred temperature range, excursion/recovery durations, soil strategy (`EVENLY_MOIST`/`DRY_BETWEEN_WATERING`) and index thresholds. A change creates a new version so historical assessments keep referencing the one that produced them. Seeded for the six herbs by V27.
- **`crop_sensor_assignment`** (V11) / **`sensor_calibration`** (V12) — which probe serves which crop, and that probe's measured dry/wet references, both versioned. Seeded by V13 from the real ADR-020 measurements. Separate tables because sensor identity and crop assignment change independently.
- **`assessment_lifecycle_event`** (V14) — full-snapshot row per assessment transition (`RAISED`/`UPDATED`/`RESOLVED`/`REOPENED`), written by `AssessmentReconciler` in the same transaction as its upsert. This is what makes the mutable `assessment` row a rebuildable projection rather than an unlogged source of truth.
- **`care_loop`** (V16) + `care_loop_assessment` + `care_loop_status_event` (V17) — the correlation root for one condition and everything responding to it. A partial unique index prevents duplicate open loops per correlation key. Status is projected from events, never stored.
- **`loop_record_scope_event`** (V18) — whether a record is relevant to a given loop. Append-only; effective scope is the latest event for that loop-record pair. Deliberately separate from lifecycle and approval.
- **`decision`** (V19) + `decision_lifecycle_event` (V20) + `decision_assessment`/`decision_goal` — immutable proposals; an amendment is a new row with `supersedes_decision_id`, and approval is an appended event.
- **`command`** (V21) + `command_lifecycle_event` (V22) — issued only from an approved decision. A `CHECK (target_type = 'HUMAN')` constraint enforces at the database level that no actuator command can exist, and a unique index on `decision_id` means a retried approval cannot issue a second command.
- **`execution`** (V23) — what the human actually did, kept separate from what the command requested so "asked for 300ml, gave 200ml" survives as two facts.
- **`outcome`** (V24) + `outcome_review_event` (V25) + `outcome_evaluation_schedule` — evidence-based evaluation; the schedule is a table rather than an in-memory timer so pending evaluations survive a restart.
- **`idempotent_request`** (V26) — one row per care-loop MCP write, so a retry replays the stored result instead of re-running the action.
- **`daily_briefing_snapshot`** (V28) — one immutable structured briefing per greenhouse day. Deliberately *not* unique per day: regeneration creates a new version linked via `supersedes_snapshot_id`.
- **`notification_intent`** (V29) — an immutable statement that a message should be delivered, anchored to exactly one care loop *or* one briefing snapshot (a `CHECK` constraint enforces the exclusive-or). `payload_json` (JSONB) captures what the renderer needs at decision time. A unique index on `deduplication_key` is the actual idempotency guarantee — repeated sweeps over unchanged state compute the same key and lose the insert race harmlessly. See ADR-023.
- **`notification_delivery_event`** (V29) — append-only, one row per delivery transition (`ATTEMPTED`/`SENT`/`FAILED`/`SUPPRESSED`/`ABANDONED`) with `attempt_number` and a persisted `next_attempt_at`, so the retry schedule survives a restart. `error_code`/`error_message` are sanitised before storage; credentials never reach these columns.

`goal`, `harvest`, `crop_observation`, and `action` all have real foreign keys to `crop(id)` — unlike `observation`/`device`/`soil_moisture_reading`, they're only ever written through validated domain services, never raw ingestion, so the FK-avoidance rationale doesn't apply (see ADR-009).

## 6. APIs

All under the same origin as the UI (`http://<host>:8080`), no CORS configuration (nothing cross-origin exists).

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/heartbeats` (also `/api/heartbeats`) | Device connectivity heartbeat |
| `GET` | `/api/v1/devices` | All known devices |
| `GET` | `/api/v1/devices/{deviceId}` | One device |
| `POST` | `/api/v1/observations` (also `/api/observations`) | Submit an environmental reading, with an optional `soilMoisture` array (`{sensorId, rawAdc}`) |
| `GET` | `/api/v1/observations` | All observations |
| `GET` | `/api/v1/observations/latest` | Most recent observation |
| `GET` | `/api/v1/observations/{deviceId}` | Most recent observation for one device |
| `GET` | `/api/v1/soil-moisture-readings`, `/api/v1/soil-moisture-readings?sensorId=` | List soil moisture readings (optionally filtered by sensor, newest first) |
| `GET` | `/api/v1/twin` | Current facts-only Digital Twin |
| `GET` | `/api/v1/assessments?status=` | Active (default) or resolved assessments |
| `GET` | `/api/v1/state` | Composed twin + active assessments (read-only, no reconciliation side effect) |
| `POST` | `/api/v1/crops` | Create a crop |
| `GET` | `/api/v1/crops`, `/api/v1/crops/{cropId}` | List / get a crop |
| `PATCH` | `/api/v1/crops/{cropId}` | Update a crop (partial) |
| `DELETE` | `/api/v1/crops/{cropId}` | Delete a crop (only if it has no goals/harvests/observations) |
| `GET` | `/api/v1/crops/{cropId}/history` | Crop + goals + harvests + observations, composed |
| `POST`/`GET`/`DELETE` | `/api/v1/crops/{cropId}/harvests(/{harvestId})` | Record / list / delete harvests |
| `POST`/`GET`/`DELETE` | `/api/v1/crops/{cropId}/observations(/{observationId})` | Record / list / delete crop observations |
| `POST`/`GET`/`DELETE` | `/api/v1/crops/{cropId}/goals(/{goalId})` | Create / list / delete goals |
| `POST` | `/api/v1/actions` | Record an action (work performed on a crop) |
| `GET` | `/api/v1/actions`, `/api/v1/actions?cropId=`, `/api/v1/actions/{id}` | List (optionally filtered by crop, newest first) / get an action |
| `POST` | `/mcp` | MCP Streamable HTTP endpoint (bearer-token authenticated) — see §8 |
| `GET` | `/` | Read-only dashboard (static HTML/CSS/JS) |
| `GET` | `/actuator/health`, `/actuator/info` | Operational health (`info` currently returns `{}` — the env info contributor isn't populated) |

The `/api/*` paths without `/v1/` are earlier, still-supported aliases for the heartbeat/observation/device endpoints; new integrations should use the `/v1/` forms.

REST and MCP tools both call the same domain services directly (`CropService`, `GoalService`, `HarvestService`, `CropObservationService`) — neither is layered through the other.

## 7. Runtime behaviour

- Greenhouse topology — greenhouse id/name, zones, which device ids belong to which zone, environmental limits (min/max temperature and humidity), and the current/offline timing thresholds — is **configuration** (`application.yml`, `greenhouse.twin.*`), not a database table. Currently one greenhouse, one zone (`zone-main`), one device (`greenhouse-esp32-01`). An acknowledged trade-off, expected to move to persisted configuration if device count grows materially.
- Soil sensor-to-plant assignment and calibration (`greenhouse.soil-sensors.assignments` — five entries, `soil-01`..`soil-05` to Basil/Thyme/Mint/Sage/Oregano, each with a measured `dry-raw-adc`/`wet-raw-adc` reference pair) follows the same configuration-not-database-table pattern, bound via `SoilSensorProperties`. Not yet read by any code path — see ADR-018, ADR-020, and §10.
- Device status thresholds: `ONLINE` under the current-threshold (2m), `DELAYED` under the offline-threshold (5m), `OFFLINE` beyond it, `UNKNOWN` if never seen. The same thresholds drive `FreshnessStatus` for observations.
- The four assessment rules (`temperature-operating-limit`, `humidity-operating-limit`, `observation-freshness` — code `OBSERVATION_STALE`, and `device-availability` — code `DEVICE_OFFLINE`) are stateless and evaluate independently; the reconciler is what gives the results statefulness. Severity: environmental limit breaches and staleness are `WARNING`; device offline is `CRITICAL`. A device that has never reported (`UNKNOWN`) does not raise `DEVICE_OFFLINE` — only a previously-seen device going quiet does.
- Four schedulers run, on deliberately different cadences: `GreenhouseEvaluationScheduler` (1 min — twin, assessments, and care-loop correlation on one clock reading), `OutcomeEvaluationScheduler` (5 min — evaluation windows are hours-scale and per-execution), `DailyBriefingScheduler` (ticks every minute and generates only once local time has reached `generate-at`, 06:00 `Europe/London` by default), and `NotificationSweepScheduler` (5 min — notification policy then delivery, plus a startup sweep).
- The briefing schedule has exactly one source of truth: `generate-at` + `zone`. There is no separate cron property — changing `generate-at` genuinely changes when it fires. Startup recovery re-checks the same due condition, so a restart before the configured hour generates nothing rather than producing a "daily briefing" at midnight.
- Care-loop recovery is driven by loop **state**, not by a tick's deltas: every evaluation cycle scans open loops and closes one whose linked assessments are all resolved and have stayed resolved for the recovery duration. A tick that resolves nothing can still close a loop.
- The notification sweep reads care-loop and briefing state and records that a message should exist. It never mutates an assessment, decision, command, execution, outcome or scope record, and is not a second assessment engine — it re-evaluates no thresholds. Notification failing, or being disabled entirely, has no effect on care-cycle correctness. Email ships **disabled** (`greenhouse.notifications.channels.email.enabled`, default `false`); with it off, no adapter is created and no delivery is attempted. See ADR-023.
- Care loops open only once a condition has persisted for the crop's configured excursion duration (default 60 min) and close only after its recovery duration (default 30 min). The assessment itself is raised on the first cycle regardless — that separation is what stops a one-minute blip generating a task without discarding the evidence that it happened. Sensor-quality conditions (not assigned, uncalibrated, stale) are actionable immediately, since they are not transient.
- Crop temperature assessments across several crops collapse into a single greenhouse-level loop, because ventilating is one physical act regardless of how many crops are affected.
- Assessment list responses are sorted by severity rank in application code (`AssessmentQueryService`), not via a JPA-derived `ORDER BY` — `AssessmentSeverity` is `EnumType.STRING`, so a raw column sort would be alphabetical rather than by actual severity.

## 8. MCP agent interface

An MCP server runs inside the same process (`com.greenhouse.mcp`), reachable at `POST /mcp` using the Streamable HTTP transport from the official MCP Java SDK (`io.modelcontextprotocol.sdk`, hand-wired — not Spring AI's Boot starter; see ADR-015 for why). Twenty-six tools are exposed:

```
Read:   get_greenhouse_state, list_crops, get_crop, get_crop_history, list_goals, list_actions,
        get_open_care_loops, get_care_loop, get_daily_crop_status, get_notification_history
Write:  create_crop, update_crop, create_goal, record_harvest, record_crop_observation, record_action,
        propose_care_decision, record_decision_response, record_command_response,
        record_care_execution, record_outcome_review, record_loop_scope_override
Delete: delete_crop, delete_goal, delete_harvest, delete_crop_observation
```

The six care-loop write tools each require an `idempotencyKey` and route through one shared `IdempotencyService`: a retry with the same key returns the stored result rather than re-running the action, so a repeated approval cannot issue a second command. A key reused with *different* arguments is rejected as a caller bug rather than silently returning an unrelated result.

Five of them (`record_decision_response`, `record_command_response`, `record_care_execution`, `record_outcome_review`, `record_loop_scope_override`) record a human's answer, and each states in its own tool description that it may only be called after the user has explicitly said so in the current conversation, persisting as `actorType=HUMAN_VIA_AGENT`. That description text is the only thing telling a context-free agent it may not approve on the user's behalf, so `McpServerIntegrationTest` asserts each one actually carries it. `propose_care_decision` is the exception: proposing is genuinely the agent's own act and issues nothing.

`get_notification_history` is read-only by design: it reports what was decided and what happened to it, but nothing can create, resend or cancel a notification through MCP. Delivery is driven entirely by the sweep, and a second write path would be a second, racing dispatcher.

There is deliberately **no** generic `update_*`, `execute_sql` or free-query tool; a test asserts their absence.

`delete_crop` only succeeds if the crop has no recorded goals, harvests, observations, or actions — anything with real history must be retired via `update_crop` (`status: ENDED`) instead. The four leaf-record delete tools (`delete_goal`/`delete_harvest`/`delete_crop_observation`, and `Action` once it gets one) are/would be unrestricted, since a single leaf record has no children of its own. See ADR-016 and ADR-017. `Action` has no delete tool yet — not rejected, just not yet requested; it would follow the same unrestricted pattern if added.

`record_action` persists agricultural work performed on a crop (watering, feeding, pruning, pollinating, moving, planting) — distinct from `record_crop_observation` (what was seen) and `record_harvest` (what was produced). `get_crop_history` composes all four (goals, actions, harvests, observations) alongside the crop's own details, so a completely fresh MCP session can reconstruct a crop's full story — proven directly in `McpServerIntegrationTest` via two independent MCP sessions. See ADR-017.

Every tool calls a domain service directly (never a repository directly, never REST), validates its input, and maps known domain errors (`CropNotFoundException`, `GoalNotFoundException`, `HarvestNotFoundException`, `CropObservationNotFoundException`, `ActionNotFoundException`, `DomainValidationException`) to a clean text message rather than a raw exception (`McpToolSupport`). No tool executes SQL, shell commands, or file access.

**Authentication**: a bearer-token servlet filter (`McpAuthenticationFilter`) guards every request under `/mcp`; every other endpoint is unaffected. The token is supplied via `GREENHOUSE_MCP_AUTH_TOKEN` (same environment-variable-backed pattern as the database password). If unset, every `/mcp` request is rejected — the filter fails closed, never open. Full client setup: `docs/mcp/AGENT_SETUP.md`. Implementation detail: `docs/mcp/IMPLEMENTATION.md`.

## 9. UI

Single-page, read-only dashboard at `GET /`, served from `backend/src/main/resources/static/` (no separate frontend service, no build step, no framework, no third-party JS). Polls `GET /api/v1/state` every 20 seconds, pauses while the browser tab is hidden, supports manual refresh. Distinguishes API-unreachable from device-offline from twin-unknown, and retains last-known data (marked stale) rather than erasing it on a failed poll. Renders the primary zone/device (`zones[0]`) from the twin response. Full detail: `docs/ui/ui-v1.md`.

## 10. Known limitations

- **No control loop.** The platform senses, stores, models, assesses, and displays — it does not act on the physical greenhouse. MCP write tools record what a human reports; they do not control anything. See `ARCHITECTURE_PARADIGM.md` for the intended future direction.
- **Single zone / single device in practice.** The data model supports multiple zones and devices; the deployed configuration and the UI's "primary zone" rendering do not yet exercise that.
- **Topology is YAML, not persisted.** Adding a zone or device means editing `application.yml` and redeploying, not an API call.
- **No retention policy on `observation`.** The table grows unboundedly; no rollup or archival exists yet.
- **1-minute assessment evaluation cadence.** Real conditions can be up to ~1 minute stale in assessment terms even when the twin itself is current.
- **No authentication on REST or the UI** — only `/mcp` requires a bearer token. Access control for everything else is entirely at the network layer (Tailscale). Acceptable for a single-user home deployment; would need revisiting before any multi-user or public exposure.
- **`delete_crop` is deliberately narrow.** It only works on crops with zero recorded history — a real crop with goals/harvests/observations/actions can only be retired (`update_crop`, `status: ENDED`), never deleted, short of manually deleting its child records first. No bulk delete, cascade delete, or undo/soft-delete exists for any of the four delete tools.
- **Local dev and the Pi have independently-managed database credentials and MCP auth tokens** (not shared, not centrally rotated).
- **No control loop into the hardware.** Every command targets a human; the `command` table has a database-level `CHECK` constraint enforcing it. Progressive autonomy is modelled for (an `ActorType.AGENT`, a non-human `targetType`) but no such authority is enabled.
- **Moisture index is not volumetric water content.** It is a 0–100 position between one probe's own measured dry and wet references. Two probes reading 40 are not necessarily equally wet in absolute terms, and the briefing/tool descriptions say so explicitly to stop it being reported as "percent water".
- **`SoilSensorProperties` YAML is now bootstrap-only.** ADR-022 moved calibration and assignment into versioned tables; the config block still validates at startup but no runtime path reads it. It should be deleted in a later cleanup once the DB-backed path has proven itself.
- **The dashboard has not been extended.** Crop status, soil moisture and open care loops are available over REST/MCP but the UI still renders only the original twin/assessment view. `/api/v1/state` is unchanged and backwards-compatible.
- **Humidity, light and feeding produce no assessments.** Humidity is reported as a measured fact only; there are no configured numeric thresholds or persistence rules for it, and no light sensor exists at all. Feeding/pruning guidance is contextual advice, not a sensor assessment.
- **Notification delivery is at-least-once, not exactly-once.** If the process dies between the provider accepting a message and `SENT` being recorded, a retry can duplicate it. A deterministic `Message-ID` gives the receiving server a chance to collapse the duplicate, but it is not eliminated — see ADR-023 for why that trade was made deliberately.
- **Email is the only notification channel, and it is deployed disabled.** The port is channel-neutral so WhatsApp could be added as an adapter, but no second adapter exists. There is no inbound path: nothing can be actioned by replying to a message.
- **The notification sweep is 5-minutely**, so an actionable care loop can be up to five minutes old before anyone is told. Nothing in a greenhouse changes state faster than that.
- **No retention policy on `notification_intent`.** Same unbounded growth as `observation`, at a far lower rate.
- **Outcome evaluation is deliberately conservative.** A crop with no probe, no readings since the work, or an uncalibrated sensor yields `INCONCLUSIVE` with the reason recorded, never an assumed success.
