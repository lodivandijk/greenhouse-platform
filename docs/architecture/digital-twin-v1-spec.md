# Digital Twin Version 1 — Implementation Specification

## 1. Purpose

Implement the first basic digital twin for the greenhouse platform.

The current physical system consists of:

- One greenhouse
- One logical environmental zone
- One ESP32 device
- One BME280 sensor
- Temperature, humidity and pressure observations
- PostgreSQL persistence for observations
- An existing Spring Boot backend

The digital twin must provide a single coherent representation of the current greenhouse state.

It must combine:

- The latest environmental observation
- The device that produced the observation
- The age and freshness of the data
- The inferred device status
- A basic environmental assessment
- An overall greenhouse status

The digital twin must be exposed through a REST API.

---

## 2. Architectural Principle

The observation domain records what was measured.

The digital twin domain interprets what those measurements mean for the current greenhouse.

```text
ESP32 / BME280
      │
      │ HTTP observation
      ▼
Observation domain
      │
      ├── Persists observations in PostgreSQL
      └── Provides latest observation
      │
      ▼
Digital twin domain
      │
      ├── Determines data freshness
      ├── Determines device status
      ├── Assesses environmental state
      ├── Determines overall twin status
      └── Assembles the current greenhouse representation
      │
      ▼
GET /api/v1/twin
```

The twin domain must not directly query the observation JPA repository.

It must consume the observation domain through an application-level service or interface.

---

## 3. Scope

### 3.1 Included

Implement:

- One configured greenhouse
- One configured zone
- One or more configured device IDs within that zone
- Retrieval of the latest observation for each configured device
- Current temperature
- Current humidity
- Current pressure
- Data freshness calculation
- Device online, delayed, offline or unknown state
- Basic environmental assessment
- Overall greenhouse twin status
- A REST endpoint returning the current twin
- Configuration through application.yml
- Unit tests
- Controller integration tests
- Clear handling of missing observations

### 3.2 Excluded

Do not implement:

- Individual plant twins
- Crop-specific target ranges
- Irrigation recommendations
- Decision engine logic
- Execution engine logic
- Actuator control
- Weather integration
- Predictions
- AI assessment
- Redis
- WebSockets
- MQTT
- Separate microservices
- New persistent twin-state tables
- Historical twin snapshots
- A user interface

The existing observation history remains the historical source.

---

## 4. Existing Observation Model

The existing persisted PostgreSQL table is:

```text
observation
├── id                    BIGINT
├── device_id             VARCHAR(255)
├── temperature_celsius   DOUBLE PRECISION
├── humidity_percent      DOUBLE PRECISION
├── pressure_hpa          DOUBLE PRECISION
└── received_at           TIMESTAMPTZ
```

The existing observation package is expected to contain equivalents of:

```text
ObservationEntity
ObservationRepository
ObservationService
ObservationRequest
ObservationStatus
```

The twin implementation should adapt to the actual current names where necessary.

Do not unnecessarily redesign the existing observation domain.

---

## 5. Package Structure

Create the following package structure:

```text
com.greenhouse.twin
├── TwinController.java
├── TwinService.java
├── TwinAssembler.java
│
├── config
│   ├── TwinProperties.java
│   └── ZoneProperties.java
│
├── model
│   ├── GreenhouseTwin.java
│   ├── ZoneTwin.java
│   ├── EnvironmentState.java
│   ├── EnvironmentAssessment.java
│   ├── DeviceTwin.java
│   └── DataQuality.java
│
└── status
    ├── TwinStatus.java
    ├── DeviceStatus.java
    ├── FreshnessStatus.java
    ├── AssessmentLevel.java
    └── EnvironmentCondition.java
```

Use Java records for immutable API and domain representation objects where suitable.

Use constructor injection throughout.

Do not use field injection.

---

## 6. Configuration

Create configuration properties under:

```yaml
greenhouse:
  twin:
```

Example:

```yaml
greenhouse:
  twin:
    greenhouse-id: greenhouse-01
    greenhouse-name: Home Greenhouse
    current-threshold: 2m
    offline-threshold: 5m
    environmental-limits:
      minimum-temperature-celsius: 5.0
      maximum-temperature-celsius: 35.0
      minimum-humidity-percent: 25.0
      maximum-humidity-percent: 90.0
    zones:
      - zone-id: zone-main
        name: Main Greenhouse
        device-ids:
          - greenhouse-sensor-01
```

Use Spring Boot `@ConfigurationProperties`.

Enable configuration-property scanning using the existing project convention.

If no convention exists, use `@ConfigurationPropertiesScan` on the main application class.

---

## 7. Configuration Classes

### 7.1 TwinProperties

Create a configuration record or immutable class representing:

```text
String greenhouseId
String greenhouseName
Duration currentThreshold
Duration offlineThreshold
EnvironmentalLimits environmentalLimits
List<ZoneProperties> zones
```

`EnvironmentalLimits` may be a nested record or a separate configuration class.

Suggested structure:

```java
public record EnvironmentalLimits(
    double minimumTemperatureCelsius,
    double maximumTemperatureCelsius,
    double minimumHumidityPercent,
    double maximumHumidityPercent
) {}
```

Validation requirements:

- `greenhouseId` must not be blank
- `greenhouseName` must not be blank
- `currentThreshold` must be positive
- `offlineThreshold` must be greater than `currentThreshold`
- At least one zone must be configured
- Minimum temperature must be below maximum temperature
- Minimum humidity must be below maximum humidity
- Humidity limits must remain between 0 and 100

Use Jakarta validation annotations where practical.

### 7.2 ZoneProperties

Create:

```java
public record ZoneProperties(
    String zoneId,
    String name,
    List<String> deviceIds
) {}
```

Validation requirements:

- `zoneId` must not be blank
- `name` must not be blank
- At least one device ID must be configured
- Device IDs must not be blank

---

## 8. Status Enums

### 8.1 TwinStatus

```java
public enum TwinStatus {
    NORMAL,
    WARNING,
    OFFLINE,
    UNKNOWN
}
```

Meanings:

- `NORMAL`: current data is available and no environmental warning exists
- `WARNING`: current or delayed data is available, but environmental conditions are outside configured limits
- `OFFLINE`: all configured devices are offline or the environmental data is stale
- `UNKNOWN`: no observation has ever been received for any configured device

### 8.2 DeviceStatus

```java
public enum DeviceStatus {
    ONLINE,
    DELAYED,
    OFFLINE,
    UNKNOWN
}
```

Rules:

```text
No observation exists
→ UNKNOWN

Observation age < currentThreshold
→ ONLINE

Observation age >= currentThreshold
and observation age < offlineThreshold
→ DELAYED

Observation age >= offlineThreshold
→ OFFLINE
```

Boundary behaviour must be exact:

- An observation exactly equal to `currentThreshold` is `DELAYED`
- An observation exactly equal to `offlineThreshold` is `OFFLINE`

### 8.3 FreshnessStatus

```java
public enum FreshnessStatus {
    CURRENT,
    DELAYED,
    STALE,
    UNKNOWN
}
```

Rules:

```text
No observation exists
→ UNKNOWN

Observation age < currentThreshold
→ CURRENT

Observation age >= currentThreshold
and observation age < offlineThreshold
→ DELAYED

Observation age >= offlineThreshold
→ STALE
```

Freshness and device status currently use the same timestamp but must remain separate concepts.

### 8.4 AssessmentLevel

```java
public enum AssessmentLevel {
    NORMAL,
    WARNING,
    UNKNOWN
}
```

### 8.5 EnvironmentCondition

```java
public enum EnvironmentCondition {
    TOO_COLD,
    TOO_HOT,
    TOO_DRY,
    TOO_HUMID
}
```

Multiple conditions may be present simultaneously.

Example:

```json
{
  "level": "WARNING",
  "conditions": [
    "TOO_HOT",
    "TOO_DRY"
  ]
}
```

---

## 9. Domain Model

### 9.1 GreenhouseTwin

Create:

```java
public record GreenhouseTwin(
    String greenhouseId,
    String name,
    TwinStatus status,
    Instant generatedAt,
    Instant lastUpdatedAt,
    List<ZoneTwin> zones
) {}
```

Semantics:

- `generatedAt`: time at which the response was assembled
- `lastUpdatedAt`: most recent observation timestamp across all zones and devices
- `lastUpdatedAt` may be null when no observations exist
- `zones` must not be null
- `zones` should be returned in configured order

### 9.2 ZoneTwin

Create:

```java
public record ZoneTwin(
    String zoneId,
    String name,
    EnvironmentState environment,
    EnvironmentAssessment assessment,
    DataQuality dataQuality,
    List<DeviceTwin> devices
) {}
```

Semantics:

- `environment` represents the selected latest environmental observation for the zone
- `assessment` describes whether configured limits are breached
- `dataQuality` describes the selected environmental observation
- `devices` contains all configured devices in the zone

For Version 1, the zone's environmental state should be based on the newest observation from any configured device assigned to that zone.

### 9.3 EnvironmentState

Create:

```java
public record EnvironmentState(
    Double temperatureCelsius,
    Double humidityPercent,
    Double pressureHpa
) {}
```

All measurement fields must allow null.

A null value means that measurement was unavailable in the selected observation.

Do not convert null values to zero.

### 9.4 EnvironmentAssessment

Create:

```java
public record EnvironmentAssessment(
    AssessmentLevel level,
    Set<EnvironmentCondition> conditions
) {}
```

Rules:

```text
No environmental observation exists
→ level UNKNOWN
→ empty conditions

No available measurement breaches configured limits
→ level NORMAL
→ empty conditions

One or more measurements breach configured limits
→ level WARNING
→ include every applicable condition
```

Use a deterministic set implementation or ensure stable JSON ordering.

Preferred condition order:

1. `TOO_COLD`
2. `TOO_HOT`
3. `TOO_DRY`
4. `TOO_HUMID`

Pressure is reported but is not assessed in Version 1.

Temperature rules:

```text
temperature < minimumTemperatureCelsius
→ TOO_COLD

temperature > maximumTemperatureCelsius
→ TOO_HOT
```

Humidity rules:

```text
humidity < minimumHumidityPercent
→ TOO_DRY

humidity > maximumHumidityPercent
→ TOO_HUMID
```

Configured boundary values are considered acceptable.

For example, if maximum temperature is 35.0°C:

```text
35.0°C  → acceptable
35.1°C  → TOO_HOT
```

Null measurements must not create a warning.

### 9.5 DeviceTwin

Create:

```java
public record DeviceTwin(
    String deviceId,
    String name,
    String deviceType,
    DeviceStatus status,
    Instant lastSeenAt
) {}
```

For Version 1:

- `name` can be derived from or default to the device ID
- `deviceType` may default to `ESP32_BME280`
- `lastSeenAt` is the timestamp of the latest observation for that device
- `lastSeenAt` is null if no observation exists

Do not introduce a persistent device table as part of this implementation.

A future device domain can replace the configured defaults.

### 9.6 DataQuality

Create:

```java
public record DataQuality(
    FreshnessStatus freshness,
    Long ageSeconds,
    Instant observedAt,
    boolean complete
) {}
```

Rules:

- `freshness` is based on observation age
- `ageSeconds` is the non-negative whole number of seconds between `observedAt` and `generatedAt`
- `ageSeconds` is null when no observation exists
- `observedAt` is null when no observation exists
- `complete` is true only when temperature, humidity and pressure are all non-null

If the observation timestamp is unexpectedly in the future, clamp `ageSeconds` to zero.

Do not return a negative age.

---

## 10. Observation-Domain Integration

The twin must obtain the latest observation for a device through the observation service.

Preferred existing or new service method:

```java
Optional<ObservationStatus> getLatestForDevice(String deviceId);
```

Expected observation application model:

```java
public record ObservationStatus(
    String deviceId,
    Double temperatureCelsius,
    Double humidityPercent,
    Double pressureHpa,
    Instant receivedAt
) {}
```

If the existing DTO uses different names, adapt without duplicating the domain unnecessarily.

The twin package must not import:

```text
ObservationEntity
ObservationRepository
JpaRepository
```

Acceptable dependency direction:

```text
TwinService
    ↓
ObservationService
    ↓
ObservationRepository
```

Unacceptable dependency direction:

```text
TwinService
    ↓
ObservationRepository
```

---

## 11. TwinService

### 11.1 Responsibility

`TwinService` coordinates the digital-twin use case.

It must:

1. Read the configured greenhouse and zones
2. Request the latest observation for each configured device
3. Capture one consistent `Instant now`
4. Pass configuration and observations to `TwinAssembler`
5. Return the resulting `GreenhouseTwin`

Suggested public method:

```java
public GreenhouseTwin getCurrentTwin()
```

Suggested dependencies:

```text
ObservationService observationService
TwinAssembler twinAssembler
TwinProperties twinProperties
Clock clock
```

Use an injected `Clock`.

Do not call `Instant.now()` directly inside business logic.

This allows deterministic tests.

### 11.2 Observation Retrieval

For every configured zone:

- Iterate through every configured device ID
- Retrieve the latest observation for each device
- Preserve missing observations as empty results
- Do not fail the entire twin because one device has no observation

A database or unexpected service failure may propagate as the project's standard server error.

Do not silently convert infrastructure failures into `UNKNOWN`.

---

## 12. TwinAssembler

### 12.1 Responsibility

`TwinAssembler` converts observations and configuration into the twin model.

It must contain or delegate the pure interpretation logic for:

- Observation age
- Freshness
- Device status
- Zone environmental state
- Environmental assessment
- Zone data quality
- Overall twin status
- Latest update timestamp

The assembler should not perform database queries.

Suggested method:

```java
public GreenhouseTwin assemble(
    TwinProperties properties,
    Map<String, Optional<ObservationStatus>> latestObservations,
    Instant generatedAt
)
```

Alternative signatures are acceptable if the responsibilities remain clear.

### 12.2 Zone Observation Selection

A zone may have multiple configured devices.

For Version 1:

- Build a `DeviceTwin` for every configured device
- Select the newest available observation among those devices
- Use that observation as the zone's environmental state
- Use that same observation for zone `DataQuality`
- If no device has an observation, return unknown zone state

Selection must be based on `receivedAt`.

Do not average observations in Version 1.

### 12.3 Overall Twin Status Rules

Determine the root `TwinStatus` using these rules in priority order.

**Rule 1: No observations**

If no configured device has ever produced an observation:

```text
TwinStatus.UNKNOWN
```

**Rule 2: All devices offline**

If every configured device is either `OFFLINE` or `UNKNOWN`, and at least one historical observation exists:

```text
TwinStatus.OFFLINE
```

**Rule 3: Environmental warning**

If at least one zone has `EnvironmentAssessment.level == WARNING` and its data freshness is not `STALE`:

```text
TwinStatus.WARNING
```

**Rule 4: Current or delayed healthy data**

If at least one device is `ONLINE` or `DELAYED`, and there are no active environmental warnings:

```text
TwinStatus.NORMAL
```

**Fallback**

```text
TwinStatus.UNKNOWN
```

A stale environmental warning must not continue presenting as a live warning.

If the latest data is stale, the twin should report `OFFLINE`.

---

## 13. REST Controller

### 13.1 Endpoint

Implement:

```text
GET /api/v1/twin
```

Response:

```text
200 OK
Content-Type: application/json
```

The controller must call `twinService.getCurrentTwin()`.

The controller must contain no calculation logic.

Suggested structure:

```java
@RestController
@RequestMapping("/api/v1/twin")
public class TwinController {
    private final TwinService twinService;

    public TwinController(TwinService twinService) {
        this.twinService = twinService;
    }

    @GetMapping
    public GreenhouseTwin getCurrentTwin() {
        return twinService.getCurrentTwin();
    }
}
```

---

## 14. Example API Response

### 14.1 Normal Current State

```json
{
  "greenhouseId": "greenhouse-01",
  "name": "Home Greenhouse",
  "status": "NORMAL",
  "generatedAt": "2026-07-25T06:20:00Z",
  "lastUpdatedAt": "2026-07-25T06:19:36Z",
  "zones": [
    {
      "zoneId": "zone-main",
      "name": "Main Greenhouse",
      "environment": {
        "temperatureCelsius": 22.8,
        "humidityPercent": 67.3,
        "pressureHpa": 1012.4
      },
      "assessment": {
        "level": "NORMAL",
        "conditions": []
      },
      "dataQuality": {
        "freshness": "CURRENT",
        "ageSeconds": 24,
        "observedAt": "2026-07-25T06:19:36Z",
        "complete": true
      },
      "devices": [
        {
          "deviceId": "greenhouse-sensor-01",
          "name": "greenhouse-sensor-01",
          "deviceType": "ESP32_BME280",
          "status": "ONLINE",
          "lastSeenAt": "2026-07-25T06:19:36Z"
        }
      ]
    }
  ]
}
```

### 14.2 No Observations Yet

```json
{
  "greenhouseId": "greenhouse-01",
  "name": "Home Greenhouse",
  "status": "UNKNOWN",
  "generatedAt": "2026-07-25T06:20:00Z",
  "lastUpdatedAt": null,
  "zones": [
    {
      "zoneId": "zone-main",
      "name": "Main Greenhouse",
      "environment": {
        "temperatureCelsius": null,
        "humidityPercent": null,
        "pressureHpa": null
      },
      "assessment": {
        "level": "UNKNOWN",
        "conditions": []
      },
      "dataQuality": {
        "freshness": "UNKNOWN",
        "ageSeconds": null,
        "observedAt": null,
        "complete": false
      },
      "devices": [
        {
          "deviceId": "greenhouse-sensor-01",
          "name": "greenhouse-sensor-01",
          "deviceType": "ESP32_BME280",
          "status": "UNKNOWN",
          "lastSeenAt": null
        }
      ]
    }
  ]
}
```

### 14.3 Environmental Warning

```json
{
  "greenhouseId": "greenhouse-01",
  "name": "Home Greenhouse",
  "status": "WARNING",
  "generatedAt": "2026-07-25T13:00:00Z",
  "lastUpdatedAt": "2026-07-25T12:59:30Z",
  "zones": [
    {
      "zoneId": "zone-main",
      "name": "Main Greenhouse",
      "environment": {
        "temperatureCelsius": 38.2,
        "humidityPercent": 21.0,
        "pressureHpa": 1008.4
      },
      "assessment": {
        "level": "WARNING",
        "conditions": [
          "TOO_HOT",
          "TOO_DRY"
        ]
      },
      "dataQuality": {
        "freshness": "CURRENT",
        "ageSeconds": 30,
        "observedAt": "2026-07-25T12:59:30Z",
        "complete": true
      },
      "devices": [
        {
          "deviceId": "greenhouse-sensor-01",
          "name": "greenhouse-sensor-01",
          "deviceType": "ESP32_BME280",
          "status": "ONLINE",
          "lastSeenAt": "2026-07-25T12:59:30Z"
        }
      ]
    }
  ]
}
```

### 14.4 Stale Observation

```json
{
  "greenhouseId": "greenhouse-01",
  "name": "Home Greenhouse",
  "status": "OFFLINE",
  "generatedAt": "2026-07-25T13:00:00Z",
  "lastUpdatedAt": "2026-07-25T12:50:00Z",
  "zones": [
    {
      "zoneId": "zone-main",
      "name": "Main Greenhouse",
      "environment": {
        "temperatureCelsius": 38.2,
        "humidityPercent": 21.0,
        "pressureHpa": 1008.4
      },
      "assessment": {
        "level": "WARNING",
        "conditions": [
          "TOO_HOT",
          "TOO_DRY"
        ]
      },
      "dataQuality": {
        "freshness": "STALE",
        "ageSeconds": 600,
        "observedAt": "2026-07-25T12:50:00Z",
        "complete": true
      },
      "devices": [
        {
          "deviceId": "greenhouse-sensor-01",
          "name": "greenhouse-sensor-01",
          "deviceType": "ESP32_BME280",
          "status": "OFFLINE",
          "lastSeenAt": "2026-07-25T12:50:00Z"
        }
      ]
    }
  ]
}
```

The historic assessment may still be visible, but the root status must be `OFFLINE`.

---

## 15. API Date and Time Rules

Use ISO-8601 timestamps.

Use `Instant` within the domain and API response.

Example: `2026-07-25T06:19:36Z`

Do not format timestamps into display strings such as `24 seconds ago`.

Relative display formatting belongs in the future user interface.

---

## 16. Error Handling

Use the existing application-wide exception handling convention.

Expected behaviour:

- Valid request with no observations: 200 OK with `UNKNOWN` state
- Invalid application configuration: application startup failure
- Unexpected database error: 500 Internal Server Error
- No separate 404 is required because the configured greenhouse always exists

Do not return 404 merely because no observation exists.

---

## 17. Logging

Add useful but restrained logging.

Recommended:

- Debug log when assembling a twin
- Warning log if an observation timestamp is in the future
- No warning logs for expected missing observations
- No log entry on every successful API field calculation

Do not log the full twin response on every request at info level.

---

## 18. Testing Requirements

### 18.1 TwinAssemblerTest

Create focused unit tests for pure interpretation logic.

Required cases:

**No observation** — Verify: `DeviceStatus.UNKNOWN`, `FreshnessStatus.UNKNOWN`, `AssessmentLevel.UNKNOWN`, `TwinStatus.UNKNOWN`, `lastUpdatedAt == null`

**Current normal observation** — Given: age 30s, temperature 22°C, humidity 60%, pressure 1012 hPa. Verify: `DeviceStatus.ONLINE`, `FreshnessStatus.CURRENT`, `AssessmentLevel.NORMAL`, `TwinStatus.NORMAL`, `complete == true`

**Delayed normal observation** — Given: `currentThreshold` 2 minutes, `offlineThreshold` 5 minutes, age 3 minutes. Verify: `DeviceStatus.DELAYED`, `FreshnessStatus.DELAYED`, `TwinStatus.NORMAL`

**Offline observation** — Given: age 5 minutes. Verify: `DeviceStatus.OFFLINE`, `FreshnessStatus.STALE`, `TwinStatus.OFFLINE`

**Current-threshold boundary** — Given observation age exactly equal to `currentThreshold`: `DeviceStatus.DELAYED`, `FreshnessStatus.DELAYED`

**Offline-threshold boundary** — Given observation age exactly equal to `offlineThreshold`: `DeviceStatus.OFFLINE`, `FreshnessStatus.STALE`

**Too hot** — Verify: `AssessmentLevel.WARNING`, conditions contains `TOO_HOT`, `TwinStatus.WARNING`

**Too cold** — Verify `TOO_COLD`.

**Too dry** — Verify `TOO_DRY`.

**Too humid** — Verify `TOO_HUMID`.

**Multiple warnings** — Given hot and dry conditions, verify both conditions are returned.

**Boundary environmental values** — Given values exactly equal to configured minimum and maximum limits, verify no warning.

**Null temperature** — Verify null temperature does not produce a warning.

**Incomplete observation** — Given one or more null measurement fields, verify `complete == false`.

**Future observation timestamp** — Verify `ageSeconds == 0`.

**Multiple devices in one zone** — Verify: every device appears in the response; the newest observation becomes the zone environmental state; the newest timestamp becomes zone `observedAt`.

**Multiple zones** — Verify: configured ordering is preserved; `lastUpdatedAt` is the newest observation across the greenhouse.

### 18.2 TwinServiceTest

Mock the observation service.

Verify:

- It requests the latest observation for every configured device
- It passes one consistent clock time into assembly
- Missing observations do not cause failure
- It does not query repositories directly

### 18.3 TwinControllerTest

Use MockMvc or the project's existing controller-test approach.

Verify `GET /api/v1/twin` returns:

- HTTP 200
- JSON content type
- Expected greenhouse ID
- Expected status
- Zone array
- Device array
- Environmental values
- Freshness data

Suggested assertions:

```text
$.greenhouseId
$.status
$.zones[0].zoneId
$.zones[0].environment.temperatureCelsius
$.zones[0].assessment.level
$.zones[0].dataQuality.freshness
$.zones[0].devices[0].status
```

---

## 19. Code Quality Requirements

Use:

- Java 21
- Existing Spring Boot project version
- Constructor injection
- Immutable records where appropriate
- Clock injection for time-based logic
- `Optional` at service boundaries where an observation may not exist
- Clear enum-based states
- Existing project formatting and naming conventions

Avoid:

- Lombok unless already used by the project
- Static mutable state
- Hard-coded greenhouse IDs
- Hard-coded device IDs
- Hard-coded thresholds in business logic
- Direct repository access from the twin domain
- Returning JPA entities from controllers
- Catching broad exceptions and converting them to unknown state
- Premature abstractions or microservices

---

## 20. Suggested Implementation Sequence

**Step 1: Configuration** — Create `TwinProperties`, `ZoneProperties`, `EnvironmentalLimits`, `application.yml` configuration

**Step 2: Status enums** — Create `TwinStatus`, `DeviceStatus`, `FreshnessStatus`, `AssessmentLevel`, `EnvironmentCondition`

**Step 3: Twin model** — Create `GreenhouseTwin`, `ZoneTwin`, `EnvironmentState`, `EnvironmentAssessment`, `DeviceTwin`, `DataQuality`

**Step 4: Observation service capability** — Confirm or add `Optional<ObservationStatus> getLatestForDevice(String deviceId)`. Reuse the existing repository query where possible.

**Step 5: TwinAssembler** — Implement all pure status and assembly rules.

**Step 6: TwinService** — Coordinate configuration, observation retrieval and assembly.

**Step 7: TwinController** — Expose `GET /api/v1/twin`.

**Step 8: Tests** — Add unit and controller tests.

**Step 9: Documentation** — Update the project README or API documentation with: endpoint, example response, configuration, status meanings.

---

## 21. Acceptance Criteria

The implementation is complete when all of the following are true.

**Functional**

- `GET /api/v1/twin` returns HTTP 200
- The response contains the configured greenhouse
- The response contains the configured zone
- The response contains the configured device
- Latest BME280 values are included
- Device status is inferred from observation age
- Data freshness is included
- Basic environmental warnings are calculated
- Multiple warnings can be represented simultaneously
- Missing observations produce an `UNKNOWN` twin rather than an error
- Stale observations produce an `OFFLINE` twin
- The API response does not expose JPA entities

**Architectural**

- Twin code is isolated under `com.greenhouse.twin`
- Twin code does not directly access `ObservationRepository`
- Configuration is externalised
- Time is supplied through `Clock`
- No new persistence tables are introduced
- Existing observation ingestion continues to work unchanged

**Quality**

- Tests cover status boundaries
- Tests cover missing data
- Tests cover environmental limits
- Tests cover multiple devices
- Application starts successfully with valid configuration
- Application fails clearly with invalid configuration

---

## 22. Definition of Done Demonstration

After deployment, the following command should return the current twin:

```bash
curl http://localhost:8080/api/v1/twin
```

Expected conceptual response:

```text
Home Greenhouse
├── Overall status
├── Last updated timestamp
└── Main Greenhouse Zone
    ├── Temperature
    ├── Humidity
    ├── Pressure
    ├── Environmental assessment
    ├── Data freshness
    └── Sensor device status
```

The first digital-twin milestone is achieved when this endpoint provides a reliable, understandable and current representation of the physical greenhouse and its connected BME280 sensor.

---

## 23. Instructions to Claude Code

Before changing code:

1. Inspect the current repository structure.
2. Identify the actual observation-domain class and method names.
3. Identify the existing Spring Boot configuration style.
4. Identify the current testing framework and conventions.
5. Reuse existing patterns rather than duplicating them.

Then:

1. Implement the digital twin in small, reviewable changes.
2. Preserve all existing API behaviour.
3. Run the complete test suite.
4. Resolve compilation, formatting and test failures.
5. Provide a summary of files created and changed.
6. Provide the final `/api/v1/twin` example response.
7. Highlight any deviation from this specification and explain why it was necessary.

Do not introduce unrelated refactoring.
