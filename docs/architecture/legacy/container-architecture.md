# Container Architecture

The platform is divided into several independently understandable containers.

A container in this context is a separately deployable or independently running application or data store.

## Initial containers

```text
┌───────────────────────────────────────────────────────────────┐
│                     Greenhouse Platform                       │
│                                                               │
│  ┌────────────────┐        ┌──────────────────────────────┐    │
│  │ ESP32 Firmware │───────▶│ Spring Boot Platform        │    │
│  │                │ HTTP   │                              │    │
│  │ Sensors        │        │ REST API                     │    │
│  │ Heartbeat      │        │ Device Registry             │    │
│  │ Local control  │        │ Observation Processing      │    │
│  └────────────────┘        │ Digital Twin                │    │
│                            │ Automation                  │    │
│                            └──────────────┬───────────────┘    │
│                                           │                    │
│                                           ▼                    │
│                                 ┌──────────────────┐            │
│                                 │ Data Store       │            │
│                                 │                  │            │
│                                 │ Observations     │            │
│                                 │ Twin state       │            │
│                                 │ Decisions        │            │
│                                 └──────────────────┘            │
│                                                               │
│  ┌────────────────┐                                           │
│  │ Dashboard      │◀───────────────────────────────────────────┤
│  │                │       REST/API                             │
│  │ Monitoring     │                                           │
│  │ Planning       │                                           │
│  │ Control        │                                           │
│  └────────────────┘                                           │
└───────────────────────────────────────────────────────────────┘
```

## ESP32 firmware

The firmware runs on edge devices deployed around the greenhouse.

Responsibilities:

- Connect to the local network.
- Identify the device.
- Read attached sensors.
- Report observations.
- Report device health.
- Receive approved commands.
- Execute local safety behaviour.
- Continue basic operation during temporary backend unavailability.

The ESP32 firmware should remain generic. Device behaviour should be determined through configuration and assigned capabilities.

## Spring Boot platform

The Spring Boot application is the initial local platform backend.

Responsibilities:

- Expose REST APIs.
- Receive device heartbeats.
- Receive sensor observations.
- Maintain device status.
- Update the Digital Twin.
- Execute business rules.
- Coordinate automation.
- Store historical records.
- Expose data to dashboards and AI services.
- Record decision provenance.

The first iteration contains only:

- Health endpoint.
- Heartbeat endpoint.
- In-memory device registry.
- Device-status endpoints.

## Data store

The data store will eventually persist:

- Devices.
- Sensor observations.
- Current Digital Twin state.
- Historical state transitions.
- Crops and planting records.
- Irrigation events.
- Automation decisions.
- User actions.
- Recommendations.
- Equipment and maintenance records.

The initial heartbeat iteration does not require a database.

## Dashboard

The dashboard provides a human-facing view of the platform.

Initial capabilities:

- Device status.
- Last heartbeat.
- Signal strength.
- Latest sensor values.

Later capabilities:

- Greenhouse layout.
- Bed and crop status.
- Irrigation control.
- Alerts.
- Historical charts.
- Growing calendar.
- AI recommendations.
- Decision explanations.

## AI services

AI is treated as a consumer of platform information, not as the owner of operational state.

The AI layer may:

- Compare observed performance with global growing knowledge.
- Recommend planting plans.
- Identify unusual conditions.
- Explain crop performance.
- Suggest automation-policy changes.
- Identify commercially attractive crops.

AI does not communicate directly with pumps, valves or ESP32 nodes.
