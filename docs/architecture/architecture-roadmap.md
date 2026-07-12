# Architecture Roadmap

## Stage 1 — Connected device foundation

Current focus:

- ESP32 identity.
- Wi-Fi.
- Logging.
- Heartbeat.
- Spring Boot backend.
- Device registry.
- Raspberry Pi deployment.

Result:

```text
ESP32 → Spring Boot → device status
```

## Stage 2 — Environmental sensing

Add:

- BME280.
- Temperature.
- Humidity.
- Pressure.
- Observation API.
- Historical storage.
- Basic charts.

Result:

```text
Sensors → observations → history → dashboard
```

## Stage 3 — Growing model

Add:

- Greenhouse entity.
- Growing areas.
- Crops.
- Plantings.
- Growing calendar.
- Digital Twin state.

Result:

```text
Observations → Digital Twin → crop context
```

## Stage 4 — Irrigation

Add:

- Soil moisture.
- Water storage.
- Pumps.
- Valves.
- Irrigation zones.
- Safety rules.
- Manual approval.
- Automation policies.

Result:

```text
Twin state → policy → irrigation command
```

## Stage 5 — Intelligence

Add:

- Global growing knowledge.
- Comparison with local outcomes.
- Recommendations.
- Anomaly detection.
- Predictive models.
- Explainable automation.

Result:

```text
Digital Twin + history + knowledge → recommendations
```

## Stage 6 — Commercial scaling

Add:

- Multiple greenhouses.
- Multiple sites.
- Crop demand planning.
- Production forecasting.
- Quality tracking.
- Customer and restaurant supply.
- Operational reporting.
- Staff workflows.

Result:

```text
Domestic platform → commercial CEA platform
```
