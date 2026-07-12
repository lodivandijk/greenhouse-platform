# System Context

The Greenhouse Platform monitors and manages a controlled growing environment.

Its initial deployment is a domestic greenhouse, but the platform is intended to support larger greenhouses, polytunnels and future commercial growing environments.

## Primary users

The principal user is the greenhouse operator.

The operator needs to:

- Understand current greenhouse conditions.
- Review historical environmental data.
- Plan planting and harvesting.
- Receive recommendations.
- Monitor equipment and sensors.
- Control or approve automation.
- Understand why automated decisions were made.

Future users may include:

- Commercial growers.
- Agronomists.
- Maintenance staff.
- Restaurant or customer account managers.
- Platform administrators.

## External systems

The platform may interact with:

- ESP32 edge devices.
- Environmental sensors.
- Irrigation pumps and valves.
- Local weather services.
- Forecasting services.
- Calendar systems.
- AI services.
- Notification services.
- Future commercial order or inventory systems.

## System-context view

```text
                         ┌──────────────────────┐
                         │ Greenhouse Operator  │
                         └──────────┬───────────┘
                                    │
                                    │ views, plans, controls
                                    ▼
                  ┌──────────────────────────────────┐
                  │       Greenhouse Platform        │
                  │                                  │
                  │ Monitoring                       │
                  │ Digital Twin                     │
                  │ Automation                       │
                  │ Planning                         │
                  │ Recommendations                  │
                  └───────┬───────────────┬──────────┘
                          │               │
            observations │               │ commands
                          ▼               ▼
                ┌────────────────┐   ┌─────────────────┐
                │ Sensors and    │   │ Pumps, valves,  │
                │ ESP32 nodes    │   │ fans and other  │
                │                │   │ actuators       │
                └────────────────┘   └─────────────────┘

                          ▲
                          │ weather and external data
                          │
                 ┌────────────────────┐
                 │ External services  │
                 └────────────────────┘
```

## System boundary

The Greenhouse Platform owns:

- Device registration.
- Observation ingestion.
- Operational state.
- The Digital Twin.
- Business rules.
- Automation policies.
- Historical data.
- Recommendations.
- User-facing APIs.
- Dashboards.
- Decision audit history.

Edge devices do not own greenhouse state. They measure conditions, execute authorised commands, and report their own health.
