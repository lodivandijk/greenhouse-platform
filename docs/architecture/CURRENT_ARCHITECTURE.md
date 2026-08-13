# Current Architecture

This document describes **only implemented, deployed reality**. It is the authoritative description of what the system looks like today — not what is planned or being considered. See `ARCHITECTURE_PARADIGM.md` for how this document relates to the ADRs in `decisions/`, and for the longer-term direction this system is expected to grow toward.

For exact implementation details, the source code and tests remain authoritative over this document.

---

## 1. Purpose

A monitoring platform for a home greenhouse: an ESP32 sensor node reports environmental readings and connectivity heartbeats to a Spring Boot backend, which persists them, derives current state, evaluates that state against configured operating limits, and presents the result on a read-only dashboard. The system is currently **observational, not autonomous** — there is no path back from software into the physical greenhouse.

```
SENSE → STORE → MODEL → ASSESS → DISPLAY
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

- **Firmware** (`firmware/GreenhouseESP32/`): Arduino sketch. Connects to home Wi-Fi, POSTs a heartbeat and BME280 readings (temperature/humidity/pressure) on a timer, retries/logs failures. Symlinked into the Arduino IDE's sketchbook for local development.
- **Backend** (`backend/`): single Spring Boot application, single deployable JAR (`greenhouse-platform.jar`), containing both the REST API and the static UI. No microservices, no separate frontend process.
- **Database**: PostgreSQL, one instance on the Pi (production) and one on the development Mac, kept schema-identical via the same Flyway migrations. Never exposed outside `localhost` — the backend is the only client, even over Tailscale.
- **Network**: Tailscale connects the Mac and the Pi over an encrypted private mesh. No SSH, PostgreSQL, or application port is exposed to the public internet. `ssh greenhouse-pi` works via a `~/.ssh/config` alias to the Tailscale IP (MagicDNS is not wired into this Mac's OS resolver because of the CLI-only Homebrew Tailscale install, so plain hostname lookups from `curl`/`ping` on this Mac specifically don't resolve — see `docs/operations/remote-access.md`).
- **Deployment**: `scripts/deploy.sh` — refuses to run on a dirty git tree, builds and tests locally, `scp`s the jar to `/opt/greenhouse/`, then over SSH backs up the previous jar, restarts the systemd unit, and polls `/actuator/health` until healthy.

## 3. Domain boundaries (backend packages)

```
com.greenhouse.heartbeat     Device connectivity heartbeats (ingestion)
com.greenhouse.device        Device registry / connectivity status (persisted)
com.greenhouse.observation   Environmental readings (persisted, append-only)
com.greenhouse.twin          Digital Twin — current factual state, assembled per request
com.greenhouse.assessment    Assessment Engine — interpretation, persisted lifecycle
com.greenhouse.evaluation    Scheduler + coordinator orchestrating twin → assessment
com.greenhouse.state         Composed read model (twin + active assessments)
com.greenhouse.common        Cross-cutting (API exception handling)
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

PostgreSQL, three tables via Flyway migrations (`backend/src/main/resources/db/migration/`), `spring.jpa.hibernate.ddl-auto=validate` (schema changes only happen through a migration, never Hibernate auto-DDL):

- **`observation`** (V1) — append-only log. One row per accepted reading: `device_id`, `temperature_celsius`, `humidity_percent`, `pressure_hpa`, `received_at`. Rows are never updated or deleted by the ingestion path. No foreign key to `device` (ingestion must keep working even if a device record is temporarily missing).
- **`device`** (V2) — one mutable row per device, natural key (`device_id`, no surrogate id): `software_version`, `first_seen_at`, `last_seen_at`, `last_ip_address`, `last_signal_strength_dbm`, `last_uptime_seconds`, `heartbeat_count`, `enabled`, `updated_at`. `online` is never stored — always derived at read time from `last_seen_at`.
- **`assessment`** (V3) — lifecycle-tracked. `correlation_key`, `greenhouse_id`/`zone_id`/`device_id`, `scope_type`/`scope_id`, `code`, `severity`, `status`, `message`, `evidence_json` (JSONB), `rule_id`/`rule_version`, `first_detected_at`/`last_detected_at`/`last_evaluated_at`/`resolved_at`. A **partial unique index** — `UNIQUE (correlation_key) WHERE status = 'ACTIVE'` — enforces at most one active record per logical condition at the database level while still allowing historical recurrence.

## 6. APIs

All under the same origin as the UI (`http://<host>:8080`), no CORS configuration (nothing cross-origin exists).

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/heartbeats` (also `/api/heartbeats`) | Device connectivity heartbeat |
| `GET` | `/api/v1/devices` | All known devices |
| `GET` | `/api/v1/devices/{deviceId}` | One device |
| `POST` | `/api/v1/observations` (also `/api/observations`) | Submit an environmental reading |
| `GET` | `/api/v1/observations` | All observations |
| `GET` | `/api/v1/observations/latest` | Most recent observation |
| `GET` | `/api/v1/observations/{deviceId}` | Most recent observation for one device |
| `GET` | `/api/v1/twin` | Current facts-only Digital Twin |
| `GET` | `/api/v1/assessments?status=` | Active (default) or resolved assessments |
| `GET` | `/api/v1/state` | Composed twin + active assessments (read-only, no reconciliation side effect) |
| `GET` | `/` | Read-only dashboard (static HTML/CSS/JS) |
| `GET` | `/actuator/health`, `/actuator/info` | Operational health (`info` currently returns `{}` — the env info contributor isn't populated) |

The `/api/*` paths without `/v1/` are earlier, still-supported aliases for the heartbeat/observation/device endpoints; new integrations should use the `/v1/` forms.

## 7. Runtime behaviour

- Greenhouse topology — greenhouse id/name, zones, which device ids belong to which zone, environmental limits (min/max temperature and humidity), and the current/offline timing thresholds — is **configuration** (`application.yml`, `greenhouse.twin.*`), not a database table. Currently one greenhouse, one zone (`zone-main`), one device (`greenhouse-esp32-01`). An acknowledged trade-off, expected to move to persisted configuration if device count grows materially.
- Device status thresholds: `ONLINE` under the current-threshold (2m), `DELAYED` under the offline-threshold (5m), `OFFLINE` beyond it, `UNKNOWN` if never seen. The same thresholds drive `FreshnessStatus` for observations.
- The four assessment rules (`temperature-operating-limit`, `humidity-operating-limit`, `observation-freshness` — code `OBSERVATION_STALE`, and `device-availability` — code `DEVICE_OFFLINE`) are stateless and evaluate independently; the reconciler is what gives the results statefulness. Severity: environmental limit breaches and staleness are `WARNING`; device offline is `CRITICAL`. A device that has never reported (`UNKNOWN`) does not raise `DEVICE_OFFLINE` — only a previously-seen device going quiet does.
- Assessment list responses are sorted by severity rank in application code (`AssessmentQueryService`), not via a JPA-derived `ORDER BY` — `AssessmentSeverity` is `EnumType.STRING`, so a raw column sort would be alphabetical rather than by actual severity.

## 8. UI

Single-page, read-only dashboard at `GET /`, served from `backend/src/main/resources/static/` (no separate frontend service, no build step, no framework, no third-party JS). Polls `GET /api/v1/state` every 20 seconds, pauses while the browser tab is hidden, supports manual refresh. Distinguishes API-unreachable from device-offline from twin-unknown, and retains last-known data (marked stale) rather than erasing it on a failed poll. Renders the primary zone/device (`zones[0]`) from the twin response. Full detail: `docs/ui/ui-v1.md`.

## 9. Known limitations

- **No control loop.** The platform senses, stores, models, assesses, and displays — it does not act on the physical greenhouse. See `ARCHITECTURE_PARADIGM.md` for the intended future direction.
- **Single zone / single device in practice.** The data model supports multiple zones and devices; the deployed configuration and the UI's "primary zone" rendering do not yet exercise that.
- **Topology is YAML, not persisted.** Adding a zone or device means editing `application.yml` and redeploying, not an API call.
- **No retention policy on `observation`.** The table grows unboundedly; no rollup or archival exists yet.
- **1-minute assessment evaluation cadence.** Real conditions can be up to ~1 minute stale in assessment terms even when the twin itself is current.
- **No authentication anywhere.** Access control is entirely at the network layer (Tailscale). Acceptable for a single-user home deployment; would need revisiting before any multi-user or public exposure.
- **Local dev and the Pi have independently-managed database credentials** (not shared, not centrally rotated).
