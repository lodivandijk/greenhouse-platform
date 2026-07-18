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

Independently, on its own fixed timer (`Config::OBSERVATION_INTERVAL_MS`), the device reads temperature/humidity/pressure from the BME280 and POSTs it to `/api/observations`. Observations are skipped (with a logged warning) if the sensor wasn't detected at boot or if Wi-Fi is disconnected, and resume automatically once conditions recover.
