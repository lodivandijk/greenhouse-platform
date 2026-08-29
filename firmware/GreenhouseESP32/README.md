# GreenhouseESP32

Arduino firmware for the greenhouse sensor node.

## Hardware

- Board: ESP32-PICO-KIT-1 V1.0
- Arduino board selection: ESP32 PICO-D4
- Sensor: BME280 (temperature/humidity/pressure) over I2C

### BME280 wiring

| BME280 pin | ESP32-PICO-KIT pin |
|------------|--------------------|
| VIN        | 3.3V               |
| GND        | GND                |
| SCL        | GPIO22             |
| SDA        | GPIO21             |

The sketch expects the sensor at I2C address `0x76` (`Config::BME280_I2C_ADDRESS`). Some breakout boards default to `0x77` instead — update `Config.h` if the sensor isn't detected at boot.

### Soil moisture wiring

Capacitive Soil Moisture Sensor v1.2 probes, one per GPIO, all sharing the 3.3V and GND rails. Only 5 physical sensors exist — a planned sixth (tarragon, GPIO39) will not be wired. Each sensor's *initial* plant mapping is shown for reference only — it lives in the backend's `SoilSensorProperties` config, not in firmware, so re-assigning a probe to a different plant never requires a reflash (see ADR-018):

| Sensor ID | ESP32 GPIO | Initial plant mapping |
|-----------|------------|----------------|
| `soil-01` | GPIO34 | Basil |
| `soil-02` | GPIO33 | Thyme |
| `soil-03` | GPIO32 | Mint |
| `soil-04` | GPIO35 | Sage |
| `soil-05` | GPIO36 / VP | Oregano |

The sensor-to-GPIO mapping lives in `SoilMoistureSensor.h` (`SoilSensors::ALL`) — that's the single firmware source of truth for *hardware* identity. Sensors 1-4 (`soil-01`..`soil-04`) are physically wired as of this writing; `SoilSensors::ACTIVE_COUNT` controls how many of the five are actually read, since an unconnected analogue input floats and produces meaningless values. Raise it to 5 once `soil-05` is wired in.

Raw 12-bit ADC values for every active sensor are included in each observation POST (`soilMoisture: [{sensorId, rawAdc}, ...]`, see `docs/architecture/soil-moisture-sensor-integration-v2-spec.md` section 7) and still logged to Serial for diagnosis. No moisture percentage or dry/wet classification is derived anywhere in firmware. Unwired sensors (beyond `SoilSensors::ACTIVE_COUNT`) are simply absent from the array, never reported with a fabricated value — see the comment on `SoilMoistureSensor` for why there's no per-reading failure sentinel.

## Setup

1. Copy `Secrets.example.h` to `Secrets.h` and fill in your Wi-Fi SSID/password. `Secrets.h` is gitignored and never committed.
2. Update `Config.h` if the backend's host/port, heartbeat/observation interval, or BME280 I2C address differ from the defaults.
3. Install the `Adafruit BME280 Library` (Library Manager, or `arduino-cli lib install "Adafruit BME280 Library"`) — this pulls in `Adafruit Unified Sensor` and `Adafruit BusIO` as dependencies.
4. Open `GreenhouseESP32.ino` in the Arduino IDE (File > Open Sketch, any folder works) and upload, or compile from the CLI:

```bash
arduino-cli compile --fqbn esp32:esp32:pico32 firmware/GreenhouseESP32
```

## Behavior

On boot, the device connects to Wi-Fi, then on a fixed timer (`Config::HEARTBEAT_INTERVAL_MS`) POSTs a heartbeat to the backend's `/api/heartbeats` endpoint. Heartbeats are skipped while Wi-Fi is disconnected and resume automatically once it reconnects. Diagnostics are always logged to Serial regardless of network state.

Independently, on its own fixed timer (`Config::OBSERVATION_INTERVAL_MS`), the device reads temperature/humidity/pressure from the BME280, reads every active soil moisture sensor (logging each to Serial as `soil sensor id=... gpio=... rawAdc=...`), and POSTs all of it together as one `/api/observations` payload. There is deliberately no separate timer or second network call for soil data — see `SensorService::sendObservation`. Observations are skipped entirely (with a logged warning) if the BME280 sensor wasn't detected at boot or if Wi-Fi is disconnected, and resume automatically once conditions recover.
