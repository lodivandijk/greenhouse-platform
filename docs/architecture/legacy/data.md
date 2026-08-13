# Greenhouse Platform – Data Model (Current + Target)

**Version:** 0.4  
**Status:** Living Architecture Document

---

# Purpose

This document describes the current data model implemented by the Greenhouse Platform and the planned evolution of the model.

The design philosophy is:

> **Observations are immutable facts. Everything else is derived from those facts.**

---

# Current Architecture

```
ESP32
   │
   ├── Heartbeat
   │        │
   │        ▼
   │   Device Registry
   │   (In Memory)
   │
   └── Observation
            │
            ▼
      Spring Boot
            │
            ▼
      PostgreSQL
```

Only environmental observations are currently persisted.

---

# Current Persistent Model

## observation

Stores every environmental reading received from every device.

| Column | Type | Notes |
|---------|------|------|
| id | BIGINT | Primary Key |
| device_id | VARCHAR(255) | Source device identifier |
| temperature_celsius | DOUBLE PRECISION | Nullable |
| humidity_percent | DOUBLE PRECISION | Nullable (0–100) |
| pressure_hpa | DOUBLE PRECISION | Nullable |
| received_at | TIMESTAMPTZ | Time received by backend |

### Indexes

```
(device_id, received_at DESC)
```

Optimised for:

- latest observation per device
- observation history
- recent observations

---

# Current Java Model

```
com.greenhouse.observation

ObservationEntity
ObservationRepository
ObservationService

ObservationRequest
ObservationStatus
```

Responsibilities

**ObservationEntity**

Maps directly to the observation table.

**ObservationRepository**

Provides persistence.

Examples:

- latest per device
- latest overall
- history by device

**ObservationService**

Business layer between API and persistence.

**ObservationRequest**

Incoming REST payload.

**ObservationStatus**

Outgoing API DTO.

---

# Current In-Memory Model

Device state is currently **not persisted**.

```
ConcurrentHashMap<String, DeviceStatus>
```

```
DeviceStatus

deviceId
softwareVersion
ipAddress
signalStrengthDbm
uptimeSeconds

firstSeenAt
lastSeenAt

heartbeatCount

online
```

Managed by:

```
DeviceRegistry
```

Properties

- Lost on backend restart
- Used by `/devices`
- Used by `/heartbeats`

---

# Proposed Iteration 4

## Persist Device State

The Device Registry will move from memory into PostgreSQL.

```
ESP32

Heartbeat

↓

Spring Boot

↓

DeviceService

↓

PostgreSQL
```

The REST API does **not** change.

Only the storage implementation changes.

---

# Proposed device Table

```
device
```

| Column | Type | Notes |
|---------|------|------|
| device_id | VARCHAR(255) | Primary Key |
| software_version | VARCHAR(100) | Latest firmware |
| first_seen_at | TIMESTAMPTZ | Initial registration |
| last_seen_at | TIMESTAMPTZ | Last heartbeat |
| last_ip_address | VARCHAR(45) | IPv4 / IPv6 |
| last_signal_strength_dbm | INTEGER | Latest RSSI |
| last_uptime_seconds | BIGINT | Latest uptime |
| heartbeat_count | BIGINT | Incremented each heartbeat |
| enabled | BOOLEAN | Device enabled flag |
| updated_at | TIMESTAMPTZ | Audit timestamp |

---

# Derived Properties

The following values should **not** be stored.

```
online
```

Derived using

```
last_seen_at >= now() - interval '2 minutes'
```

This prevents stale state from being persisted.

---

# Observation Relationship

Initially there will be **no foreign key**.

```
Observation

device_id
```

will remain a simple identifier.

Reason:

The platform should continue accepting observations even if:

- a heartbeat has not yet arrived
- a new device appears unexpectedly
- a device record is temporarily unavailable

This keeps ingestion resilient.

---

# Future Digital Twin

After device persistence is complete, the platform will introduce the Digital Twin.

```
environment_state
```

Example

| Column |
|---------|
| id |
| location_id |
| source_device_id |
| temperature_c |
| humidity_pct |
| pressure_hpa |
| observation_recorded_at |
| state_updated_at |
| freshness_status |

Unlike observations, this table represents the **current interpreted state**, not history.

---

# Future Domain Model

```
Device
│
├── Observation
│
├── Sensor
│
├── Environment State (Digital Twin)
│
├── Rule
│
├── Assessment
│
├── Decision
│
├── Action
│
└── Crop
```

---

# Guiding Principles

## 1. Observations are immutable

A received observation is never modified.

It represents a fact recorded at a specific time.

---

## 2. Device state is mutable

Each heartbeat updates the latest known state of a device.

---

## 3. Digital Twin is derived

The Digital Twin is generated from observations.

It is not the source of truth.

---

## 4. Decisions never modify observations

Rules, assessments and automation operate on the Digital Twin.

Historical observations remain unchanged.

---

# Target Data Flow

```
ESP32
    │
    ▼
Observation API
    │
    ▼
Observation Table
    │
    ▼
Digital Twin
    │
    ▼
Decision Engine
    │
    ▼
Automation
```

---

# Implementation Roadmap

## ✅ Iteration 1

- Heartbeats
- Device Registry (memory)

---

## ✅ Iteration 2

- Environmental observations

---

## ✅ Iteration 3

- PostgreSQL persistence

---

## 🔄 Iteration 4

- Persist Device Registry
- Device table
- Remove in-memory registry

---

## Planned

Iteration 5

- Dashboard

Iteration 6

- Digital Twin persistence

Iteration 7

- Rules Engine

Iteration 8

- Decision Engine

Iteration 9

- Irrigation automation

Iteration 10

- AI recommendations

---

# Architectural Philosophy

The platform is designed around a clear separation of concerns:

- **Observations** record immutable environmental facts.
- **Devices** represent the current state of physical sensor nodes.
- **The Digital Twin** represents the latest interpreted state of the greenhouse.
- **Rules and Decisions** consume the Digital Twin to determine what actions, if any, should be taken.
- **Automation** executes those actions through actuators.

This separation allows the platform to evolve from a simple environmental monitoring system into a full Controlled Environment Agriculture (CEA) platform without changing the fundamental data model.