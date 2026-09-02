# ADR-025: Authenticated REST Writes, Per-Device Ingestion Credentials, Open Reads

**Status:** Accepted
**Date:** 2026-08-31

## Context

`McpAuthenticationFilter` guarded `/mcp` and explicitly passed everything else through anonymously. Its own comment said so: *"Every other REST endpoint is untouched."* That was a deliberate scoping decision at the MCP milestone, on the understanding that the Raspberry Pi was reachable only over Tailscale and that network isolation was doing the work.

That understanding was wrong. Checking the deployed Pi:

- The application binds `0.0.0.0:8080`.
- The Pi has three interfaces: `eth0 192.168.1.113`, `wlan0 192.168.1.114`, and `tailscale0 100.77.67.92`.
- `ufw` is not installed and no firewall rules exist.

An unauthenticated `GET http://192.168.1.113:8080/api/v1/crops` from another machine on the house WiFi returned `200`. Tailscale was not the only path to the application; it was one of three. Every route that creates, changes or deletes a crop, goal, harvest, observation or action was open to anything on the local network — a guest's phone, a smart bulb, anything that joins the WiFi.

The ingestion endpoints (`/api/v1/heartbeats`, `/api/v1/observations`) were equally open. That matters more than it first appears: those readings become the Digital Twin, which becomes assessments, which open care loops and send email. Forged telemetry is not just bad data, it is a false fact injected at the bottom of a chain the whole platform is built to trust.

## Decision

Three route classes, two credentials, enforced by a plain servlet filter.

### Reads and the dashboard stay open on the local network

This is an explicit policy choice, not an omission.

The read surface exposes temperature, humidity, crop species and care-loop state for a domestic herb greenhouse. The value of glancing at the dashboard from any phone in the house is real and immediate; the harm from a neighbour learning that the basil is warm is not. Authenticating reads would mean putting a credential into a browser on every device the household uses, which in practice means writing it down somewhere worse.

This is a judgement about *this* deployment, and it is the part of this ADR most likely to need revisiting — if the platform ever leaves a single private home, or the read model ever carries something personal, reads should be authenticated too.

`/actuator/health` stays reachable so the deploy script can wait for cutover, but `show-details` is now `never`: an anonymous caller has no business knowing the database vendor and version or how much disk remains.

### Writes require an administrative token

Every `POST`, `PUT`, `PATCH` and `DELETE` outside device ingestion requires `Authorization: Bearer <admin token>`. The token is environment-backed (`GREENHOUSE_ADMIN_TOKEN`), lives only in the Pi's root-readable env file alongside the database password and the MCP token, and is never committed.

Enforcement fails **closed**: if `admin-auth-required` is true and no token is configured, the application refuses to start rather than silently serving an open write surface. This mirrors the email validator's stance — running for a week wrongly is worse than not starting.

### Devices get their own credentials, one per device

Ingestion authenticates against a per-device token map (`greenhouse.security.device-tokens.<deviceId>`), not the admin token. A probe sits in a greenhouse where anyone can physically reach it; its credential should buy the right to report *its own* readings and nothing else. A device token cannot create a crop, and the admin token cannot post telemetry.

Per-device rather than one shared device token, because otherwise compromising the one accessible node would let an attacker forge readings for every other node. `DeviceIdentityGuard` closes the matching gap in the other direction: an authenticated device that claims a different `deviceId` in its payload is rejected, so device two cannot forge readings attributed to device one.

Blank tokens are dropped during binding rather than treated as real. An unset environment variable binds as an empty string, and accepting that as a credential would authenticate anybody who sent a bare `Bearer `.

### A deliberate, temporary migration window

`device-auth-required` defaults to **true**, so a fresh deployment is never accidentally open. But the deployed ESP32 sends no credential, and enforcing device auth before the firmware is updated would stop telemetry, take the device offline, raise assessments and send email about a problem this change caused.

So device enforcement is relaxed to warn-only for exactly one deployment, via `GREENHOUSE_DEVICE_AUTH_REQUIRED=false` on the Pi, while firmware 0.2.0 is flashed over OTA. Every anonymous reading accepted in that window is logged as a warning naming the path and remote address, so the state announces itself rather than being quietly forgotten. It is a migration state, not a resting state, and the env var is removed once firmware is confirmed reporting with its token.

Firmware 0.2.0 also logs a rejected credential (401/403) as an **error**, because the previous code treated any HTTP response as success — a wrong token would have looked healthy while the greenhouse went silent, and the first sign would have been a `DEVICE_OFFLINE` assessment an hour later.

### Still no Spring Security

Three route classes and two credentials do not need a framework. Adding Spring Security would be a larger change than the problem, would restructure the filter chain, and would bring an authorization model this application has no use for — there is one user. This follows the precedent set for MCP, and should be revisited if the platform ever grows real multi-user authorization rather than bolted onto that day.

## Consequences

**Good:**
- The write surface is closed. Joining the WiFi no longer lets you delete a crop.
- Forged telemetry now requires a stolen per-device credential, not merely a route to port 8080.
- Compromising one device does not let you impersonate another.
- Credentials are environment-backed and separately revocable: rotating the device token does not disturb MCP or admin access.

**Costs and limits:**
- **Tokens travel in cleartext over the LAN.** The ESP32 talks plain HTTP; there is no TLS anywhere inside the house. Anyone who can passively capture WiFi traffic can capture a device token and replay it. Fixing this properly means TLS on the Pi and certificate handling on the ESP32, which is a much larger change. This ADR reduces the attack surface from "anyone who can reach the port" to "anyone who can capture traffic or read the firmware" — a real improvement, and explicitly not the end state.
- **Reads remain open**, by choice, as argued above.
- The test suite runs with enforcement relaxed (set once in `build.gradle`) so that twenty controller tests do not each need a security block and its own cached application context. The filter's decisions are covered directly by `ApiAuthenticationTest`, which turns enforcement back on.
- One more credential to manage, and a firmware flash required to rotate a device token.
