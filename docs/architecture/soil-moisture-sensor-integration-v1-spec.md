> **Superseded by [`soil-moisture-sensor-integration-v2-spec.md`](soil-moisture-sensor-integration-v2-spec.md)** for the sensor count, stable sensor IDs, GPIO mapping, and sensor-to-plant assignment approach — scope grew from 3 probes (`soil-basil-1/2/3`) to 6 probes (`soil-01`..`soil-06`, one herb each). Kept for historical detail: the architecture principles here (raw ADC only, stable identity, telemetry vs. crop-domain separation, phased rollout) are unchanged and still apply. Phase A firmware and backend Phase B implementation work described against this v1 document (ADR-018) is being revised in place to match v2, not reimplemented from scratch.

# Soil-Moisture Sensor Integration — Claude Code Handover

Project: Greenhouse Platform
Date: 29 August 2026
Status: One soil-moisture sensor is physically connected; firmware and backend integration are not yet implemented.

## 1. Objective

Integrate three capacitive soil-moisture sensors into the existing greenhouse platform without disrupting the working BME280 telemetry, heartbeat, digital twin, assessment engine or UI.

The implementation should be staged:

1. Prove all three analogue sensors locally in the ESP32 firmware.
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
| Sensor 1 | `soil-basil-1` | 34 | ADC1_CH6 | Basil pot 1 |
| Sensor 2 | `soil-basil-2` | 33 | ADC1_CH5 | Basil pot 2 |
| Sensor 3 | `soil-basil-3` | 32 | ADC1_CH4 | Basil pot 3 |

All three probes share:

- Red/VCC → ESP32 3.3 V breadboard rail.
- Black/GND → common ground rail.
- Yellow/AOUT → the sensor-specific GPIO above.

GPIO34 is input-only, which is appropriate for this use. GPIO32, GPIO33 and GPIO34 are ADC1 inputs, so they remain usable while Wi-Fi is active.

The common v1.2 probe has no required power/status light. Lack of illumination does not indicate a fault.

## 4. Important Design Decisions

### 4.1 Preserve raw readings

The primary value collected and persisted must initially be the raw 12-bit ADC reading, nominally 0–4095.

Do not convert readings to a moisture percentage in the first implementation. Each inexpensive probe can have a different dry and wet response. Converting all three with one guessed mapping would produce misleading data.

### 4.2 Give every probe a stable identity

Use `soil-basil-1`, `soil-basil-2` and `soil-basil-3` as the initial stable sensor IDs. GPIO numbers are hardware configuration, not durable domain identity.

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

void configureSoilSensors() {
    analogReadResolution(12);
    analogSetPinAttenuation(SOIL_1_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_2_PIN, ADC_11db);
    analogSetPinAttenuation(SOIL_3_PIN, ADC_11db);
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

Prefer a small configuration structure over three unrelated variables:

```cpp
struct SoilSensorConfig {
    const char* sensorId;
    uint8_t gpio;
};

constexpr SoilSensorConfig SOIL_SENSORS[] = {
    {"soil-basil-1", 34},
    {"soil-basil-2", 33},
    {"soil-basil-3", 32}
};
```

This mapping should be the single firmware source of truth.

### 5.4 Add diagnostic logging

During the physical test, log each sensor ID, GPIO and raw value over serial. Preserve all existing BME280, heartbeat and Wi-Fi behaviour.

Example output:

```
soil sensor id=soil-basil-1 gpio=34 rawAdc=2870
soil sensor id=soil-basil-2 gpio=33 rawAdc=2915
soil sensor id=soil-basil-3 gpio=32 rawAdc=2842
```

If only sensor 1 is connected, read only GPIO34. Unconnected analogue inputs float and will return meaningless values.

### 5.5 Phase A acceptance criteria

- Existing firmware builds and flashes successfully.
- BME280 collection and current Pi posts continue unchanged.
- GPIO34 produces a repeatable raw value in air.
- GPIO34 changes materially when the sensing blade is inserted into damp soil.
- Once connected, GPIO33 and GPIO32 behave independently in the same way.
- No sensor reports a permanently saturated 0 or 4095 under normal test conditions.
- No moisture percentage or dry/wet assessment has been introduced.

Stop after Phase A if the user has not yet physically validated all three probes.

## 6. Phase B — Observation Contract and Pi Integration

Begin Phase B only after the three probes produce sensible serial readings.

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
    { "sensorId": "soil-basil-1", "rawAdc": 2870 },
    { "sensorId": "soil-basil-2", "rawAdc": 2915 },
    { "sensorId": "soil-basil-3", "rawAdc": 2842 }
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

Persist each physical sensor reading independently. Three readings in one ESP32 report should result in three independently addressable soil telemetry records or three typed readings under the same observation aggregate.

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
- Payload with three sensors persists all three without overwriting.
- Each sensor retains its stable `sensorId`.
- ADC values 0 and 4095 are accepted.
- Values below 0 or above 4095 are rejected.
- Duplicate sensor IDs in one payload are rejected or deterministically handled.
- A missing/empty soil collection is backward compatible.
- Transaction failure does not leave a partially persisted set of sensor readings.
- Existing observation, twin, assessment and controller tests remain green.

## 7. Firmware-to-Backend Integration

After the backend accepts the optional collection:

- Extend the ESP32's existing observation DTO/JSON construction to include the three `sensorId`/`rawAdc` pairs.
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
- The UI may display the three basil-pot moisture states.

These should be separate increments. Do not place policy thresholds or watering recommendations inside the firmware or facts-only twin.

## 10. Deployment Sequence

Use this order:

1. Implement and test Phase A locally.
2. Flash the ESP32 and validate each physical probe through serial output.
3. Implement the backward-compatible backend contract, migration and tests.
4. Deploy the backend/database change to the Pi first.
5. Verify the old firmware still posts successfully.
6. Update the ESP32 payload and flash the integrated firmware.
7. Verify three persisted readings per observation cycle.
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

- All three physical probes are reliably sampled on GPIO34, GPIO33 and GPIO32.
- Each probe has a stable, persistent sensor identity.
- Raw ADC readings are transmitted alongside existing environmental telemetry.
- The Pi accepts old and new payloads.
- PostgreSQL stores all three readings independently with timestamps.
- Existing platform behaviour is unaffected.
- Tests cover compatibility, validation and multi-sensor persistence.
- The implemented architecture is accurately documented.
- No uncalibrated percentage or automated watering decision is presented as fact.
