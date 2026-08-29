> **Supersedes [`soil-moisture-sensor-integration-v1-spec.md`](soil-moisture-sensor-integration-v1-spec.md)**: scope grew from 3 probes (one basil pot each) to 6 probes (one herb each: basil, thyme, mint, sage, oregano, tarragon), with new stable sensor IDs (`soil-01`..`soil-06`), a GPIO remap (three new pins), and a new explicit requirement (§4.2) that the sensor-to-plant assignment must live in Pi-side configuration, never hardcoded in ESP32 firmware. The architecture principles are unchanged from v1.
>
> **Revised 2026-08-29 (in place, not a new version — see `docs/architecture/README.md` on when a spec revision needs a new file):** the sixth probe (`soil-06`, GPIO39, tarragon) will not be wired — the user has only 5 physical sensors. This document, the firmware, and the backend config now reflect **5 sensors** (`soil-01`..`soil-05`: basil, thyme, mint, sage, oregano). GPIO39 is unused.

# Soil-Moisture Sensor Integration — Claude Code Handover

Project: Greenhouse Platform
Date: 29 August 2026
Status: One soil-moisture sensor is physically connected; firmware and backend integration are not yet implemented.

## 1. Objective

Integrate five capacitive soil-moisture sensors into the existing greenhouse platform without disrupting the working BME280 telemetry, heartbeat, digital twin, assessment engine or UI. Each sensor initially monitors one herb: basil, thyme, mint, sage or oregano.

The implementation should be staged:

1. Prove all five analogue sensors locally in the ESP32 firmware.
2. Preserve and transmit raw readings with stable sensor identities.
3. Extend the Raspberry Pi backend and PostgreSQL persistence in a backward-compatible way.
4. Calibrate each physical probe independently before deriving moisture percentages.
5. Add digital-twin exposure, assessments and UI presentation only after the readings are calibrated and reliable.

## 2. Current Project Context

The repository is `greenhouse-platform` and contains at least:

- `firmware/` for the ESP32 firmware.
- `backend/` for the Java 21 / Spring Boot 3 application running on the Raspberry Pi.
- `docs/architecture/CURRENT_ARCHITECTURE.md` for the current implemented architecture.
- Architecture Decision Records governed by `docs/architecture/ARCHITECTURE_PARADIGM.md`.

Current relevant behaviour:

- The ESP32-PICO-KIT collects BME280 temperature, humidity and pressure readings.
- The ESP32 posts observations to the Pi approximately every 60 seconds.
- The Pi persists sensor telemetry in PostgreSQL 17.
- The digital twin is facts-only.
- Assessments are derived separately from the digital twin.
- Crop-domain `CropObservation` records exist separately from raw sensor telemetry.
- The current dashboard consumes `/api/v1/state`.

Before editing, read the architecture documents and inspect the actual firmware, observation API, DTOs, persistence entities, database migrations and tests. Reuse an existing generic sensor-reading abstraction if one already exists. Do not create a parallel model that duplicates an existing capability.

## 3. Physical Wiring and Stable Identities

The board is an ESP32-PICO-KIT v4/v4.1. The sensors are Capacitive Soil Moisture Sensor v1.2 analogue probes.

| Physical sensor | Stable sensor ID | ESP32 GPIO | ADC channel | Initial plant mapping |
|---|---|---|---|---|
| Sensor 1 | `soil-01` | 34 | ADC1_CH6 | Basil |
| Sensor 2 | `soil-02` | 33 | ADC1_CH5 | Thyme |
| Sensor 3 | `soil-03` | 32 | ADC1_CH4 | Mint |
| Sensor 4 | `soil-04` | 35 | ADC1_CH7 | Sage |
| Sensor 5 | `soil-05` | 36 / VP | ADC1_CH0 | Oregano |

Only 5 physical sensors are available; a sixth (`soil-06`, GPIO39, tarragon) was planned in an earlier draft but will not be wired. GPIO39 is unused.

All five probes share:

- Red/VCC → ESP32 3.3 V breadboard rail.
- Black/GND → common ground rail.
- Yellow/AOUT → the sensor-specific GPIO above.

GPIO34, GPIO35 and GPIO36 are input-only, which is appropriate for this use. GPIO32, GPIO33, GPIO34, GPIO35 and GPIO36 are ADC1 inputs, so they remain usable while Wi-Fi is active.

The common v1.2 probe has no required power/status light. Lack of illumination does not indicate a fault.

## 4. Important Design Decisions

### 4.1 Preserve raw readings

The primary value collected and persisted must initially be the raw 12-bit ADC reading, nominally 0–4095.

Do not convert readings to a moisture percentage in the first implementation. Each inexpensive probe can have a different dry and wet response. Converting all five with one guessed mapping would produce misleading data.

### 4.2 Give every probe a stable identity

Use `soil-01` through `soil-05` as stable physical sensor IDs. GPIO numbers and current plant assignments are configuration, not durable sensor identity.

Keep the plant assignment separate from the physical sensor identity. For example, `soil-01` initially monitors basil, but it must remain `soil-01` if the plant is later replaced or moved. Label both ends of every cable with the stable sensor ID.

Represent the sensor-to-plant assignment in the Pi-side configuration or existing crop/container model after inspecting the repository. Do not hard-code plant names into the ESP32 firmware.

### 4.3 Keep this as sensor telemetry

These measurements are automated hardware telemetry. They must not be stored as crop-domain `CropObservation` records.

If there is already a generic telemetry/metric observation model, extend it. Otherwise, introduce a dedicated soil-moisture telemetry model rather than adding fixed `soil_1`, `soil_2` and `soil_3` columns to the existing BME observation table.

### 4.4 Keep derived facts separate

Raw ADC value, calibrated millivolts and observed time are facts. Moisture percentage, DRY, NORMAL or WET classifications, and watering recommendations are derived values or assessments. Do not mix these into the first persistence change.

### 4.5 Record any material architecture decision

If the work introduces a new persistent telemetry model, API structure or relationship to the existing observation aggregate, create an ADR in accordance with `ARCHITECTURE_PARADIGM.md`, then update `CURRENT_ARCHITECTURE.md` to reflect the implemented state.

## 5. Phase A — ESP32 Diagnostic Implementation

Phase A changes only the ESP32 firmware. Do not require a Pi/backend change to prove the wiring.

### 5.1 Configure the ADC inputs

For Arduino-ESP32 firmware, configure:

```cpp
constexpr uint8_t SOIL_1_PIN = 34;
constexpr uint8_t SOIL_2_PIN = 33;
constexpr uint8_t SOIL_3_PIN = 32;
constexpr uint8_t SOIL_4_PIN = 35;
constexpr uint8_t SOIL_5_PIN = 36;

void configureSoilSensors() {
    analogReadResolution(12);
    analogSetPinAttenuation(SOIL_1_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_2_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_3_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_4_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_5_PIN, ADC_11db);
}
```

Use `ADC_11db` explicitly because the probes may produce an output close to 3.0 V when dry. Do not rely on an implicit framework default.

### 5.2 Average multiple samples

The readings will be noisy. Start with a simple averaged one-shot reading:

```cpp
uint16_t readSoilSensor(uint8_t pin) {
    analogRead(pin); // Discard the first reading after selecting a channel.
    delay(2);

    uint32_t total = 0;
    constexpr int sampleCount = 16;

    for (int i = 0; i < sampleCount; ++i) {
        total += analogRead(pin);
        delay(2);
    }

    return static_cast<uint16_t>(total / sampleCount);
}
```

Keep the implementation small. Continuous ADC mode, advanced filtering and power switching are not required for this bench-test phase.

### 5.3 Introduce explicit configuration

Prefer a small configuration structure over five unrelated variables:

```cpp
struct SoilSensorConfig {
    const char* sensorId;
    uint8_t gpio;
};

constexpr SoilSensorConfig SOIL_SENSORS[] = {
    {"soil-01", 34},
    {"soil-02", 33},
    {"soil-03", 32},
    {"soil-04", 35},
    {"soil-05", 36}
};
```

This mapping should be the single firmware source of truth.

### 5.4 Add diagnostic logging

During the physical test, log each sensor ID, GPIO and raw value over serial. Preserve all existing BME280, heartbeat and Wi-Fi behaviour.

Example output:

```
soil sensor id=soil-01 gpio=34 rawAdc=2870
soil sensor id=soil-02 gpio=33 rawAdc=2915
soil sensor id=soil-03 gpio=32 rawAdc=2842
soil sensor id=soil-04 gpio=35 rawAdc=2891
soil sensor id=soil-05 gpio=36 rawAdc=2930
```

If only sensor 1 is connected, read only GPIO34. Unconnected analogue inputs float and will return meaningless values.

### 5.5 Phase A acceptance criteria

- Existing firmware builds and flashes successfully.
- BME280 collection and current Pi posts continue unchanged.
- GPIO34 produces a repeatable raw value in air.
- GPIO34 changes materially when the sensing blade is inserted into damp soil.
- Once connected, GPIO33, GPIO32, GPIO35 and GPIO36 behave independently in the same way.
- No sensor reports a permanently saturated 0 or 4095 under normal test conditions.
- No moisture percentage or dry/wet assessment has been introduced.

Stop after Phase A if the user has not yet physically validated all five probes.

## 6. Phase B — Observation Contract and Pi Integration

Begin Phase B only after the five probes produce sensible serial readings.

### 6.1 Backward-compatible observation payload

Inspect the current observation JSON and extend it rather than replacing it. The soil-moisture collection must be optional so currently deployed firmware payloads remain valid.

Target logical shape:

```json
{
  "deviceId": "greenhouse-esp32-01",
  "observedAt": "2026-08-29T09:00:00Z",
  "temperatureCelsius": 21.8,
  "humidityPercent": 68.4,
  "pressureHpa": 1014.2,
  "soilMoisture": [
    { "sensorId": "soil-01", "rawAdc": 2870 },
    { "sensorId": "soil-02", "rawAdc": 2915 },
    { "sensorId": "soil-03", "rawAdc": 2842 },
    { "sensorId": "soil-04", "rawAdc": 2891 },
    { "sensorId": "soil-05", "rawAdc": 2930 }
  ]
}
```

Use the repository's existing JSON naming and timestamp conventions if they differ from this example.

GPIO does not need to be transmitted on every observation if the sensor-to-GPIO mapping is represented in firmware/device configuration. `sensorId` is the durable identity.

### 6.2 Persistence model

First check whether the repository already supports generic typed sensor readings. If so, use that model.

If not, introduce a dedicated soil-moisture observation table/entity with the logical fields:

| Field | Purpose |
|---|---|
| `id` | Observation identity |
| `device_id` | ESP32/device identity |
| `sensor_id` | Stable physical probe identity |
| `raw_adc` | Original 12-bit reading |
| `millivolts` | Optional future calibrated voltage; nullable initially |
| `observed_at` | Time measured by the device/envelope |
| `received_at` | Time accepted by the backend |

Apply the repository's existing approach to IDs, timestamps, migrations, naming and parent observation relationships. A child relationship to the current environmental observation aggregate is acceptable if it matches the implemented model; a separate telemetry table is also acceptable. Avoid fixed per-pot columns.

### 6.3 Validation

Backend validation should initially enforce:

- `sensorId` is present and non-blank.
- `rawAdc` is between 0 and 4095, inclusive.
- Sensor IDs are unique within one observation payload.
- The optional soil-moisture array may be absent or empty for backward compatibility.
- Unknown sensor IDs are handled according to the project's existing device-configuration policy; do not invent a second registry.

### 6.4 Persistence and retrieval

Persist each physical sensor reading independently. Five readings in one ESP32 report should result in five independently addressable soil telemetry records or five typed readings under the same observation aggregate.

Do not yet:

- Generate a moisture percentage.
- Add watering advice.
- Create dry/wet assessments.
- Change the dashboard.
- Make the digital twin depend on soil readings.

### 6.5 Backend tests

Add tests covering:

- Existing BME-only payload remains accepted.
- Payload with one soil sensor is accepted and persisted.
- Payload with five sensors persists all five without overwriting.
- Each sensor retains its stable `sensorId`.
- ADC values 0 and 4095 are accepted.
- Values below 0 or above 4095 are rejected.
- Duplicate sensor IDs in one payload are rejected or deterministically handled.
- A missing/empty soil collection is backward compatible.
- Transaction failure does not leave a partially persisted set of sensor readings.
- Existing observation, twin, assessment and controller tests remain green.

## 7. Firmware-to-Backend Integration

After the backend accepts the optional collection:

- Extend the ESP32's existing observation DTO/JSON construction to include the five `sensorId`/`rawAdc` pairs.
- Retain the current observation cadence; do not introduce a second high-frequency network loop.
- Continue local serial output during the first deployment for diagnosis.
- Ensure a temporary sensor failure does not prevent BME telemetry or heartbeat transmission.
- Decide how an unavailable sensor is represented using an explicit absence/error state. Do not silently submit 0, because 0 is a valid ADC boundary value.

## 8. Calibration Follow-up — Separate Change

Calibration is deliberately outside the first integration.

For each physical probe, collect at least:

- A stable dry/air reference.
- A stable reading in the intended growing medium at a known wet condition.
- Repeated readings over several minutes to understand noise and drift.

Store calibration per `sensorId`, preferably in Pi-side configuration or a calibration entity rather than hard-coding it in the firmware. Preserve raw readings permanently so calibration logic can be improved later without losing the original measurement.

The usual direction for this sensor type is:

- Higher raw value → drier.
- Lower raw value → wetter.

Confirm this empirically for each probe before implementing percentages.

## 9. Digital Twin, Assessments and UI — Deferred

After calibration is validated:

- The digital twin may expose the latest factual value and freshness for each configured soil sensor.
- The assessment engine may derive DRY, NORMAL, WET, sensor-stale or sensor-fault assessments using crop/goal context.
- `/api/v1/state` may expose those facts and assessments.
- The UI may display the latest factual moisture value and derived state for basil, thyme, mint, sage and oregano.

These should be separate increments. Do not place policy thresholds or watering recommendations inside the firmware or facts-only twin.

## 10. Deployment Sequence

Use this order:

1. Implement and test Phase A locally.
2. Flash the ESP32 and validate each physical probe through serial output.
3. Implement the backward-compatible backend contract, migration and tests.
4. Deploy the backend/database change to the Pi first.
5. Verify the old firmware still posts successfully.
6. Update the ESP32 payload and flash the integrated firmware.
7. Verify five persisted readings per observation cycle.
8. Verify BME telemetry, heartbeat, digital twin, assessments and UI remain operational.
9. Update architecture documentation and create an ADR if required.
10. Commit and push only after tests and live verification succeed.

## 11. Out of Scope

Do not include the following in this change unless separately requested:

- Irrigation control or pump commands.
- Automatic watering decisions.
- Moisture-percentage claims before calibration.
- Crop-specific moisture thresholds.
- UI redesign.
- Replacing the existing BME observation path.
- Power switching or deep-sleep optimisation.
- Connecting any probe directly to the Raspberry Pi.

## 12. Definition of Done

The increment is complete when:

- All five physical probes are reliably sampled on GPIO34, GPIO33, GPIO32, GPIO35 and GPIO36.
- Each probe has a stable, persistent sensor identity.
- Raw ADC readings are transmitted alongside existing environmental telemetry.
- The Pi accepts old and new payloads.
- PostgreSQL stores all five readings independently with timestamps.
- Existing platform behaviour is unaffected.
- Tests cover compatibility, validation and multi-sensor persistence.
- The implemented architecture is accurately documented.
- No uncalibrated percentage or automated watering decision is presented as fact.
