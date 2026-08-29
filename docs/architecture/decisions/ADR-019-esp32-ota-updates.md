# ADR-019: ESP32 Over-the-Air Firmware Updates

**Status:** Accepted
**Date:** 2026-08-29

## Context

Every firmware change so far (BME280, heartbeat, soil moisture) has required physically connecting the ESP32 to a laptop over USB to compile and flash. That's a real friction point for iterating on sensor tuning (e.g. bumping `SoilSensors::ACTIVE_COUNT` as more probes get wired, or landing calibration once it's ready) once the device is mounted near the plants rather than sitting on a desk.

The ESP32 Arduino core's default partition scheme for this board (`esp32:esp32:pico32`, confirmed via `arduino-cli board details`) already lays out two OTA app slots (`app0`/`app1`, 0x140000 = 1,310,720 bytes each — this is exactly the "Maximum is 1310720 bytes" every compile has reported) plus an `otadata` partition. No partition change is needed; the hardware/flash layout has been OTA-capable the whole time. What was missing was firmware code to actually listen for a pushed update.

## Decision

Add `ArduinoOTA` (bundled with the ESP32 Arduino core, no new library dependency) via a new `OtaService` class, following the same one-class-per-concern pattern as `GreenhouseWiFi`/`HeartbeatService`/`SensorService`. `OtaService::begin()` is called once at startup, only if Wi-Fi connected successfully — this device is not built to gracefully recover OTA availability after a failed boot-time connection; a reboot with working Wi-Fi is what's needed, matching the project's general "keep it small" bias over building resilience nothing has asked for yet. `OtaService::update()` (`ArduinoOTA.handle()`) runs every loop iteration alongside the other services.

**Authentication**: `ArduinoOTA.setPassword(...)`, sourced from a new `Secrets::OTA_PASSWORD` constant — same gitignored-`Secrets.h`-not-committed pattern already used for Wi-Fi credentials, with `Secrets.example.h` updated as a template. There is no additional transport encryption or certificate-based auth (ArduinoOTA's basic auth challenge only). This mirrors the reasoning already accepted for REST/UI having no auth (`CURRENT_ARCHITECTURE.md` §10: "Access control for everything else is entirely at the network layer... Acceptable for a single-user home deployment"): the ESP32 is only reachable from the local home Wi-Fi, which is itself the trust boundary. A password stops accidental/casual pushes, not a determined attacker already on the LAN — accepted for the same reason the rest of this system accepts that boundary.

The very first OTA-capable build must still be flashed via USB — a device with no OTA listener obviously can't receive one over the air. This is a one-time bootstrap, not an ongoing limitation.

## Consequences

- Future firmware changes (calibration once ready, activating `soil-05`, anything else) can be pushed via `arduino-cli upload --upload-field password=... -p <device-ip> --fqbn esp32:esp32:pico32 firmware/GreenhouseESP32` once the device is running this build, instead of requiring physical USB access.
- New attack surface: anyone on the home Wi-Fi with the OTA password can push arbitrary firmware to this device. Accepted for a single-user home network; would need real transport security before this device was ever exposed beyond the home LAN (it never should be — nothing about OTA changes the existing "not exposed to the internet" boundary, since discovery is mDNS on the local subnet only).
- `OTA_PASSWORD` joins `WIFI_NAME`/`WIFI_PASSWORD` in the existing gitignored `Secrets.h` — no new secret-handling pattern introduced.
- Binary size grew from 1,083,560 to 1,139,087 bytes (82% → 86% of the 1.25MB OTA app slot) — still real headroom, but worth watching as more code is added.
- If an OTA push is interrupted or corrupted, the device falls back to whichever `app0`/`app1` slot `otadata` still points at (standard ESP32 OTA rollback behavior) rather than bricking — not specifically tested here, but this is the platform's documented behavior for this partition scheme, not something this project built.

## Related / superseded decisions

Extends the network-perimeter-as-trust-boundary reasoning already accepted in `CURRENT_ARCHITECTURE.md` §10 for REST/UI auth, applied here to firmware updates instead of HTTP endpoints. No prior ADR covered firmware deployment mechanics, so this is a new decision area rather than a supersession.
