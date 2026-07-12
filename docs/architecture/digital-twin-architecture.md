# Digital Twin Architecture

The Digital Twin is the operational representation of the greenhouse.

It is not simply a copy of sensor values.

It combines:

- Physical structure.
- Devices.
- Sensors.
- Actuators.
- Crops.
- Growing areas.
- Current environmental state.
- Operational status.
- Historical context.
- Derived assessments.

## Conceptual model

```text
Greenhouse
│
├── Growing Areas
│   ├── Bed
│   ├── Trough
│   ├── Pot
│   └── Irrigation Zone
│
├── Crops
│   ├── Variety
│   ├── Planting
│   ├── Growth Stage
│   └── Expected Harvest
│
├── Devices
│   ├── ESP32 Node
│   ├── Sensor
│   └── Actuator
│
├── Environment
│   ├── Temperature
│   ├── Humidity
│   ├── Light
│   ├── Soil Moisture
│   └── Water Availability
│
└── Operations
    ├── Irrigation
    ├── Ventilation
    ├── Heating
    ├── Maintenance
    └── Recommendations
```

## Observations versus state

A temperature reading is an observation.

```text
Sensor A reported 22.4°C at 10:15.
```

The Digital Twin may derive:

```text
Tomato bed air temperature is currently within target range.
```

The distinction is important.

Observations are:

- Immutable.
- Timestamped.
- Source-specific.
- Historical facts.

Twin state is:

- Current.
- Derived.
- Domain-oriented.
- Updated as new evidence arrives.

## Twin ownership

The Spring Boot platform owns the Digital Twin.

ESP32 nodes do not independently determine the authoritative greenhouse state.

AI services do not independently own state.

Dashboards do not calculate their own version of state.

All consumers should read from the shared platform model.
