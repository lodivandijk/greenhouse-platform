# Greenhouse

Monorepo for the greenhouse project: an ESP32 sensor node reporting into a Spring Boot backend.

## Structure

- [`backend/`](backend/README.md) — Spring Boot service that receives device heartbeats and exposes device status over REST.
- [`firmware/GreenhouseESP32/`](firmware/GreenhouseESP32/README.md) — Arduino sketch for the ESP32-PICO-KIT sensor node.
- [`architecture/`](architecture/) — design docs and iteration handoffs.

## Current scope

See [architecture/greenhouse-first-iteration-handoff.md](architecture/greenhouse-first-iteration-handoff.md) for the current iteration's scope and acceptance criteria.
