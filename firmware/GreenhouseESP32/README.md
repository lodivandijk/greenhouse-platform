# GreenhouseESP32

Arduino firmware for the greenhouse sensor node.

## Hardware

- Board: ESP32-PICO-KIT-1 V1.0
- Arduino board selection: ESP32 PICO-D4

## Setup

1. Copy `Secrets.example.h` to `Secrets.h` and fill in your Wi-Fi SSID/password. `Secrets.h` is gitignored and never committed.
2. Update `Config.h` if the backend's host/port or heartbeat interval differ from the defaults.
3. Open `GreenhouseESP32.ino` in the Arduino IDE (File > Open Sketch, any folder works) and upload, or compile from the CLI:

```bash
arduino-cli compile --fqbn esp32:esp32:pico32 firmware/GreenhouseESP32
```

## Behavior

On boot, the device connects to Wi-Fi, then on a fixed timer (`Config::HEARTBEAT_INTERVAL_MS`) POSTs a heartbeat to the backend's `/api/heartbeats` endpoint. Heartbeats are skipped while Wi-Fi is disconnected and resume automatically once it reconnects. Diagnostics are always logged to Serial regardless of network state.
