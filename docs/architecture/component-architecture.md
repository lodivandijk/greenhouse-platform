# Backend Component Architecture

The Spring Boot platform should use package-by-feature organisation.

```text
backend/
└── src/main/java/com/greenhouse/
    ├── GreenhouseApplication.java
    ├── common/
    ├── device/
    ├── observation/
    ├── digitaltwin/
    ├── greenhouse/
    ├── crop/
    ├── irrigation/
    ├── automation/
    ├── calendar/
    ├── recommendation/
    └── dashboard/
```

Each feature should contain the components required to implement that capability.

For example:

```text
device/
├── DeviceController.java
├── DeviceService.java
├── DeviceRegistry.java
├── DeviceStatus.java
├── DeviceIdentity.java
└── DeviceNotFoundException.java
```

This structure is preferred over global folders such as:

```text
controller/
service/
repository/
model/
```

The package-by-feature approach keeps domain behaviour together and allows each feature to evolve independently.

## Core components

### Device management

Responsibilities:

- Register devices.
- Maintain device identity.
- Track last-seen time.
- Track firmware version.
- Track network quality.
- Determine online or offline status.
- Record device capabilities.

### Observation ingestion

Responsibilities:

- Receive measurements.
- Validate observation payloads.
- Attach server-side timestamps.
- Preserve immutable observations.
- Route observations to relevant domain services.
- Detect duplicates or malformed records.

### Digital Twin

Responsibilities:

- Maintain the current representation of the greenhouse.
- Associate devices and sensors with physical locations.
- Derive state from observations.
- Expose current state to other platform components.
- Record state transitions.

### Automation

Responsibilities:

- Evaluate policies.
- Determine whether action is required.
- Apply safety constraints.
- Create authorised commands.
- Record why the decision was made.
- Track whether the command completed successfully.

### Irrigation

Responsibilities:

- Model irrigation zones.
- Track soil and environmental conditions.
- Track water availability.
- Coordinate pumps and valves.
- Prevent unsafe or conflicting actions.
- Record watering events.

### Crop and growing calendar

Responsibilities:

- Define crop varieties.
- Record sowing and planting dates.
- Track growth stages.
- Estimate harvest periods.
- Schedule maintenance jobs.
- Compare planned and actual performance.

### Recommendation service

Responsibilities:

- Generate recommendations using platform data.
- Incorporate global horticultural knowledge.
- Compare recommendations with the operator’s local growing history.
- Record assumptions and supporting evidence.
- Avoid directly commanding physical equipment.
