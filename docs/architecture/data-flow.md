# Data Flow

## Heartbeat flow

The first implemented data flow is the device heartbeat.

```text
ESP32 boots
    │
    ▼
Connects to Wi-Fi
    │
    ▼
Builds heartbeat payload
    │
    ▼
POST /api/heartbeats
    │
    ▼
Spring Boot validates request
    │
    ▼
Device registry updated
    │
    ▼
Device status returned
    │
    ▼
GET /api/devices exposes latest status
```

Example heartbeat:

```json
{
  "deviceId": "greenhouse-esp32-01",
  "softwareVersion": "0.1.0",
  "ipAddress": "192.168.1.68",
  "signalStrengthDbm": -52,
  "uptimeSeconds": 120
}
```

## Observation flow

The future sensor-observation flow will follow the same pattern.

```text
Sensor
    │
    ▼
ESP32 reads measurement
    │
    ▼
Observation payload created
    │
    ▼
Backend validates and timestamps observation
    │
    ├──▶ Historical observation stored
    │
    └──▶ Digital Twin updated
               │
               ├──▶ Dashboard
               ├──▶ Automation policies
               └──▶ AI recommendations
```

An observation is an immutable fact.

Example:

```text
At 10:15:00 UTC, sensor X reported 22.4°C.
```

The derived Digital Twin state may later change as newer observations arrive.

## Command flow

Commands flow in the opposite direction.

```text
Digital Twin state changes
    │
    ▼
Automation policy evaluated
    │
    ▼
Safety checks applied
    │
    ▼
Command authorised
    │
    ▼
ESP32 receives command
    │
    ▼
Actuator operates
    │
    ▼
Result reported
    │
    ▼
Digital Twin updated
```

All automated commands should record:

- The triggering state.
- The policy applied.
- The resulting decision.
- Safety checks.
- The command sent.
- The execution result.
