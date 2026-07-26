# Greenhouse Platform — Assessment Engine v1 Implementation Specification

## 1. Objective

Implement the first version of the greenhouse Assessment Engine.

The Assessment Engine must consume the current Digital Twin, interpret its factual state using deterministic rules, persist assessment lifecycles, and expose assessments through APIs.

The platform will use a scheduled reconciliation model rather than introducing Kafka or a formal event platform.

The runtime flow will be:

```text
Observations
    ↓
Digital Twin
    ↓
Assessment Engine
    ↓
Persisted assessments
    ↓
Greenhouse State API
```

Every minute:

```text
Build current twin
    ↓
Run assessment rules
    ↓
Compare findings with persisted active assessments
    ↓
Raise, update or resolve assessments
```

This milestone must not implement decisions, automated actions, Kafka, MQTT or AI-agent behaviour.

---

## 2. Architectural principles

### 2.1 The Digital Twin contains facts

The Digital Twin represents the latest known factual state of the greenhouse.

Examples:

```text
Temperature: 31.2°C
Humidity: 72.4%
Observation age: 35 seconds
Device status: ONLINE
```

The twin must not determine whether these conditions are good or bad.

### 2.2 The Assessment Engine contains interpretation

The Assessment Engine interprets twin state.

Examples:

```text
TEMPERATURE_ABOVE_LIMIT
HUMIDITY_BELOW_LIMIT
OBSERVATION_STALE
DEVICE_OFFLINE
```

### 2.3 Assessments are persisted

The Digital Twin remains a calculated view and does not need to be persisted.

Assessments must be persisted because they have:

- identity
- status
- lifecycle
- evidence
- audit value
- first and last detection times
- resolution time

### 2.4 Processing uses reconciliation

The scheduled process must calculate what assessments should currently exist and reconcile that with what is stored.

It must not blindly create a new assessment every minute.

### 2.5 Rules are deterministic

Assessment Engine v1 must use ordinary Java rules.

Do not introduce:

- an LLM
- a generic AI agent
- a third-party rules engine
- Drools
- Kafka
- external message brokers

### 2.6 Maintain a modular monolith

All functionality remains inside the existing Spring Boot application.

Use package and service boundaries that would allow later extraction without designing microservices now.

---

## 3. Current platform context

The existing platform contains:

```text
com.greenhouse.observation
com.greenhouse.twin
```

The observation domain persists environmental readings in PostgreSQL.

The Digital Twin is exposed through:

```http
GET /api/v1/twin
```

The Digital Twin currently contains status, freshness and basic environmental warning logic.

Digital Twin v1 was implemented in commit:

```text
13a6996
```

Before modifying code:

1. Inspect the existing repository.
2. Review all classes under `com.greenhouse.twin`.
3. Review current tests.
4. Review Flyway migrations.
5. Review `application.yml`.
6. Preserve existing naming and coding conventions.
7. Run the complete existing test suite and confirm it passes.

Do not assume class names where the repository already contains an equivalent abstraction.

---

## 4. Target architecture

```text
ESP32
    │
    │ POST /api/v1/observations
    ▼
Observation Domain
    │
    │ latest persisted observations
    ▼
Digital Twin
    │
    │ GreenhouseTwin
    ▼
Assessment Engine
    ├── Device availability rules
    ├── Data freshness rules
    ├── Temperature rules
    └── Humidity rules
    │
    ▼
Assessment Reconciler
    ├── Raise
    ├── Update
    └── Resolve
    │
    ▼
PostgreSQL
    │
    ├── GET /api/v1/assessments
    └── GET /api/v1/state
```

The one-minute runtime flow is:

```text
Spring Scheduler
    ↓
GreenhouseEvaluationCoordinator
    ↓
TwinService.getCurrentTwin()
    ↓
AssessmentService.assessAndReconcile(twin)
    ↓
Persist assessment changes
```

The request-driven flow remains:

```text
GET /api/v1/twin
    ↓
TwinService
    ↓
Facts-only twin response
```

The composed state flow is:

```text
GET /api/v1/state
    ↓
StateService
    ├── TwinService
    └── AssessmentQueryService
    ↓
Twin + current assessments
```

---

## 5. Scope

### 5.1 Included

Implement:

- facts-only Digital Twin
- assessment domain
- deterministic assessment rules
- persisted assessment lifecycle
- scheduled one-minute reconciliation
- manual reconciliation service entry point
- active and resolved assessment statuses
- assessment query API
- composed greenhouse state API
- Flyway migration
- rule and lifecycle tests
- scheduler tests
- integration tests
- configuration validation
- logging and operational visibility

### 5.2 Excluded

Do not implement:

- Decision Engine
- Execution Engine
- actuators
- crop-specific profiles
- growth stages
- weather forecasts
- trend prediction
- disease models
- irrigation recommendations
- user acknowledgement
- suppression
- event bus
- transactional outbox
- Kafka
- MQTT
- microservices
- LLM or agent behaviour

---

## 6. Proposed package structure

Use the existing project structure where appropriate.

Add:

```text
com.greenhouse.assessment
├── AssessmentController
├── AssessmentService
├── AssessmentQueryService
├── AssessmentRepository
├── AssessmentEntity
├── AssessmentMapper
├── AssessmentResponse
├── AssessmentFinding
├── AssessmentChanges
├── AssessmentStatus
├── AssessmentSeverity
├── AssessmentCode
├── AssessmentScopeType
│
├── rule
│   ├── AssessmentRule
│   ├── TemperatureAssessmentRule
│   ├── HumidityAssessmentRule
│   ├── ObservationFreshnessAssessmentRule
│   └── DeviceAvailabilityAssessmentRule
│
└── reconciliation
    ├── AssessmentReconciler
    └── AssessmentCorrelationKeyFactory
```

Add orchestration under a neutral application-level package:

```text
com.greenhouse.evaluation
├── GreenhouseEvaluationCoordinator
├── GreenhouseEvaluationScheduler
└── EvaluationProperties
```

Add state composition:

```text
com.greenhouse.state
├── GreenhouseStateController
├── GreenhouseStateService
└── GreenhouseStateResponse
```

Do not place the coordinator inside the twin, assessment or observation domain because it orchestrates multiple domains.

---

## 7. Digital Twin refactoring

### 7.1 Make the twin facts-only

Review the current twin response and implementation.

Remove derived environmental interpretations from the Digital Twin domain.

The following types of data may remain in the twin:

- greenhouse ID
- zone ID
- device ID
- latest temperature
- latest humidity
- latest pressure
- observation timestamp
- observation age
- factual freshness classification
- factual device availability
- generated-at timestamp

The following must move to the Assessment domain:

- too hot
- too cold
- too humid
- too dry
- environmental warning severity
- warning messages
- warning codes

### 7.2 Status and freshness interpretation

Device availability and freshness classifications may remain in the twin if they are treated as factual derived state.

For example:

```text
Observation age: 35 seconds
Freshness: CURRENT
Device status: ONLINE
```

The Assessment Engine may then create operational assessments such as:

```text
OBSERVATION_STALE
DEVICE_OFFLINE
```

This preserves the distinction between:

```text
Twin:
Device is OFFLINE according to configured timing semantics.

Assessment:
Offline device requires attention.
```

### 7.3 Backward compatibility

Inspect whether current clients or tests depend on warning fields in:

```http
GET /api/v1/twin
```

Preferred approach:

- remove warning fields from the twin because this milestone explicitly establishes a facts-only contract
- update tests and documentation accordingly
- provide equivalent information through `/api/v1/state`

If backward compatibility is considered essential after inspecting the repository, temporarily mark existing fields as deprecated and ensure they are derived through the Assessment service rather than the Twin assembler.

Do not retain duplicate assessment logic in both domains.

---

## 8. Configuration

Extend the existing configuration structure.

Use the current threshold values and exact boundary semantics from Digital Twin v1.

Suggested configuration:

```yaml
greenhouse:
  evaluation:
    enabled: true
    interval: PT1M
    initial-delay: PT10S

  assessment:
    environmental-limits:
      minimum-temperature-celsius: 5.0
      maximum-temperature-celsius: 35.0
      minimum-humidity-percent: 20.0
      maximum-humidity-percent: 90.0
```

Reuse existing threshold configuration if it already has a suitable location.

Do not create duplicate configuration keys.

### 8.1 Evaluation properties

Create a configuration properties record or class:

```java
@ConfigurationProperties(prefix = "greenhouse.evaluation")
public record EvaluationProperties(
    boolean enabled,
    Duration interval,
    Duration initialDelay
) {}
```

Validate in the constructor:

- interval must not be null
- interval must be positive
- initial delay must not be null
- initial delay must not be negative

Do not use Jakarta `@Positive` on `Duration`, because it does not validate `Duration` correctly.

### 8.2 Assessment limit validation

Validate:

```text
minimum temperature < maximum temperature
minimum humidity < maximum humidity
humidity remains within 0–100
```

The application must fail fast on invalid configuration.

---

## 9. Assessment domain model

### 9.1 AssessmentStatus

For v1:

```java
public enum AssessmentStatus {
    ACTIVE,
    RESOLVED
}
```

Do not add acknowledged, suppressed or expired statuses yet.

### 9.2 AssessmentSeverity

Use:

```java
public enum AssessmentSeverity {
    ADVISORY,
    WARNING,
    CRITICAL
}
```

Suggested v1 mapping:

```text
Environmental limit breach: WARNING
Stale observation: WARNING
Offline device: CRITICAL
```

Keep severity assignment in the relevant rule.

### 9.3 AssessmentCode

Use stable machine-readable codes:

```java
public enum AssessmentCode {
    TEMPERATURE_BELOW_LIMIT,
    TEMPERATURE_ABOVE_LIMIT,
    HUMIDITY_BELOW_LIMIT,
    HUMIDITY_ABOVE_LIMIT,
    OBSERVATION_STALE,
    DEVICE_OFFLINE
}
```

Do not use user-facing messages as identity.

### 9.4 AssessmentScopeType

```java
public enum AssessmentScopeType {
    GREENHOUSE,
    ZONE,
    DEVICE
}
```

Suggested scopes:

```text
Temperature and humidity assessments: ZONE
Freshness assessment: DEVICE
Offline assessment: DEVICE
```

### 9.5 AssessmentFinding

A finding is an in-memory result from a rule.

Suggested record:

```java
public record AssessmentFinding(
    AssessmentCode code,
    AssessmentSeverity severity,
    AssessmentScopeType scopeType,
    String scopeId,
    String greenhouseId,
    String zoneId,
    String deviceId,
    String message,
    Map<String, Object> evidence,
    String ruleId,
    int ruleVersion,
    String correlationKey
) {}
```

Rules must not persist entities directly.

They return findings.

### 9.6 AssessmentChanges

Return explicit reconciliation outcomes:

```java
public record AssessmentChanges(
    List<AssessmentResponse> raised,
    List<AssessmentResponse> updated,
    List<AssessmentResponse> resolved
) {
    public boolean hasChanges() {
        return !raised.isEmpty()
            || !updated.isEmpty()
            || !resolved.isEmpty();
    }
}
```

This structure prepares the platform for future domain events without implementing event infrastructure now.

---

## 10. Persistence model

Create a Flyway migration for an `assessment` table.

Use the next available migration number after inspecting the repository.

Suggested schema:

```sql
CREATE TABLE assessment (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    correlation_key     VARCHAR(500) NOT NULL,
    greenhouse_id       VARCHAR(255) NOT NULL,
    zone_id             VARCHAR(255),
    device_id           VARCHAR(255),
    scope_type          VARCHAR(50) NOT NULL,
    scope_id            VARCHAR(255) NOT NULL,
    code                VARCHAR(100) NOT NULL,
    severity            VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    message             TEXT NOT NULL,
    evidence_json       JSONB NOT NULL,
    rule_id             VARCHAR(255) NOT NULL,
    rule_version        INTEGER NOT NULL,
    first_detected_at   TIMESTAMPTZ NOT NULL,
    last_detected_at    TIMESTAMPTZ NOT NULL,
    last_evaluated_at   TIMESTAMPTZ NOT NULL,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);
```

Add indexes:

```sql
CREATE INDEX idx_assessment_status
    ON assessment(status);

CREATE INDEX idx_assessment_scope
    ON assessment(scope_type, scope_id);

CREATE INDEX idx_assessment_code
    ON assessment(code);

CREATE INDEX idx_assessment_active_lookup
    ON assessment(correlation_key, status);
```

Prevent multiple active assessments for the same logical condition.

PostgreSQL partial unique index:

```sql
CREATE UNIQUE INDEX uq_assessment_active_correlation
    ON assessment(correlation_key)
    WHERE status = 'ACTIVE';
```

This database constraint is important for duplicate protection.

### 10.1 Evidence JSON

Persist evidence as JSONB.

Example:

```json
{
  "actualTemperatureCelsius": 36.2,
  "maximumTemperatureCelsius": 35.0,
  "observationReceivedAt": "2026-07-26T06:20:00Z",
  "observationAgeSeconds": 42
}
```

Use the project's existing JSON/JPA conventions.

Do not store JPA entities inside the JSON.

### 10.2 Entity timestamps

Set timestamps in application code using an injected `Clock`.

Do not scatter direct `Instant.now()` calls throughout the domain.

---

## 11. AssessmentRepository

Provide repository operations required for reconciliation and queries.

At minimum:

```java
List<AssessmentEntity> findAllByStatus(AssessmentStatus status);

Optional<AssessmentEntity>
    findByCorrelationKeyAndStatus(
        String correlationKey,
        AssessmentStatus status
    );

List<AssessmentEntity>
    findAllByGreenhouseIdAndStatusOrderBySeverityDescFirstDetectedAtAsc(
        String greenhouseId,
        AssessmentStatus status
    );
```

Add pageable historical queries if consistent with existing controller conventions.

Avoid loading all historical assessments during every evaluation.

---

## 12. Rule interface

Create:

```java
public interface AssessmentRule {

    List<AssessmentFinding> evaluate(
        GreenhouseTwin twin,
        Instant evaluatedAt
    );

    String ruleId();

    int ruleVersion();
}
```

All rule implementations must be stateless.

Spring may inject them as a collection:

```java
List<AssessmentRule> rules
```

Rules must:

- inspect the twin
- return zero or more findings
- not mutate the twin
- not persist data
- not call the Decision Engine
- not send notifications
- not execute actions

---

## 13. Rule semantics

Use the exact boundary semantics already established by Digital Twin v1.

Assuming the existing semantics are:

```text
value < minimum  → below limit
value > maximum  → above limit
value == minimum → normal
value == maximum → normal
```

Preserve these semantics exactly.

Confirm against current tests before implementation.

### 13.1 TemperatureAssessmentRule

For each zone with a usable current temperature:

```text
temperature < minimum
→ TEMPERATURE_BELOW_LIMIT

temperature > maximum
→ TEMPERATURE_ABOVE_LIMIT

otherwise
→ no temperature finding
```

Do not assess temperature if the source reading is considered unusable or offline.

Suggested evidence:

```json
{
  "actualTemperatureCelsius": 36.2,
  "minimumTemperatureCelsius": 5.0,
  "maximumTemperatureCelsius": 35.0,
  "observationReceivedAt": "...",
  "observationAgeSeconds": 42
}
```

Suggested message:

```text
Zone zone-main temperature of 36.2°C is above the configured maximum of 35.0°C.
```

Use consistent formatting and avoid excessive rounding.

### 13.2 HumidityAssessmentRule

For each zone with usable humidity:

```text
humidity < minimum
→ HUMIDITY_BELOW_LIMIT

humidity > maximum
→ HUMIDITY_ABOVE_LIMIT

otherwise
→ no humidity finding
```

Humidity equal to a boundary is normal.

### 13.3 ObservationFreshnessAssessmentRule

Use the freshness state calculated by the Digital Twin.

If the existing twin supports states such as:

```text
CURRENT
DELAYED
STALE
```

map only the agreed stale state to an assessment.

Suggested behaviour:

```text
CURRENT → no finding
DELAYED → no finding in v1, unless current semantics already define it as warning-worthy
STALE   → OBSERVATION_STALE
```

Do not create `OBSERVATION_STALE` when the device has already crossed the offline threshold if this would produce redundant assessments.

Preferred precedence:

```text
OFFLINE
→ DEVICE_OFFLINE only

STALE but not OFFLINE
→ OBSERVATION_STALE
```

### 13.4 DeviceAvailabilityAssessmentRule

For each configured device:

```text
ONLINE
→ no finding

DELAYED
→ no finding in v1

OFFLINE
→ DEVICE_OFFLINE
```

Use the existing exact offline boundary semantics.

### 13.5 Missing readings

Do not manufacture environmental assessments from absent values.

Examples:

```text
No temperature reading
→ no temperature limit assessment

No humidity reading
→ no humidity limit assessment
```

Device or freshness rules should represent the data-quality problem instead.

---

## 14. Correlation keys

Each finding must have a deterministic correlation key.

Suggested format:

```text
{greenhouseId}:{scopeType}:{scopeId}:{assessmentCode}
```

Examples:

```text
greenhouse-01:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT
greenhouse-01:DEVICE:bme280-01:DEVICE_OFFLINE
```

Create keys centrally through:

```text
AssessmentCorrelationKeyFactory
```

Do not concatenate keys differently in individual rules.

The correlation key must remain stable across evaluations while the same logical condition persists.

---

## 15. Reconciliation algorithm

Create `AssessmentReconciler`.

Input:

```text
Current findings
Current active persisted assessments for the greenhouse
Evaluation timestamp
```

Output:

```text
AssessmentChanges
```

### 15.1 Raise

When a finding has no active assessment with the same correlation key:

```text
Create assessment
status = ACTIVE
firstDetectedAt = evaluatedAt
lastDetectedAt = evaluatedAt
lastEvaluatedAt = evaluatedAt
resolvedAt = null
```

Add it to `raised`.

### 15.2 Update

When a finding matches an existing active assessment:

Update:

- severity
- message
- evidence
- rule ID
- rule version
- last detected time
- last evaluated time
- updated time

Do not change:

- ID
- correlation key
- first detected time
- created time

Add it to `updated`.

An update may occur every evaluation cycle, even where only timestamps change.

However, avoid unnecessary database writes if the implementation can safely distinguish between:

```text
Condition still active but no meaningful evidence change
```

and:

```text
Meaningful assessment change
```

For v1, updating `lastEvaluatedAt` each cycle is acceptable.

### 15.3 Resolve

When an active persisted assessment has no corresponding current finding:

```text
status = RESOLVED
resolvedAt = evaluatedAt
lastEvaluatedAt = evaluatedAt
updatedAt = evaluatedAt
```

Add it to `resolved`.

### 15.4 Historical recurrence

If a resolved condition occurs again later:

- create a new assessment record
- assign a new database ID
- retain the same deterministic correlation key
- keep the previous resolved record unchanged

The partial unique index permits one active record while preserving historical occurrences.

### 15.5 Transaction boundary

Assessment reconciliation must run in one database transaction.

It should:

1. Load active assessments.
2. Compare with current findings.
3. Insert, update and resolve records.
4. Commit atomically.

Handle the partial unique-index race defensively.

There should be only one scheduled evaluator in v1, but database constraints must still protect correctness.

---

## 16. AssessmentService

Suggested responsibilities:

```java
public AssessmentChanges assessAndReconcile(
    GreenhouseTwin twin,
    Instant evaluatedAt
)
```

Implementation:

```text
1. Execute every registered AssessmentRule.
2. Collect all findings.
3. Validate that correlation keys are unique.
4. Pass findings to AssessmentReconciler.
5. Persist lifecycle changes.
6. Return AssessmentChanges.
```

If two rules produce the same correlation key in one evaluation:

- fail the evaluation clearly
- log the duplicate rule IDs and correlation key
- do not silently overwrite one finding

Also provide a read-only method if useful:

```java
List<AssessmentFinding> evaluate(
    GreenhouseTwin twin,
    Instant evaluatedAt
)
```

This can support unit testing without persistence.

---

## 17. Evaluation coordinator

Create:

```java
public class GreenhouseEvaluationCoordinator {

    public EvaluationResult evaluate();
}
```

Suggested result:

```java
public record EvaluationResult(
    Instant evaluatedAt,
    String greenhouseId,
    AssessmentChanges assessmentChanges
) {}
```

Flow:

```text
1. Capture evaluatedAt from Clock.
2. Obtain current GreenhouseTwin.
3. Pass the twin to AssessmentService.
4. Log summary.
5. Return EvaluationResult.
```

The coordinator must not contain rule logic.

It is orchestration only.

The future Decision Engine will be added after assessment reconciliation:

```text
Twin
    ↓
Assessment changes
    ↓
Decision reconciliation
```

Do not implement this now.

---

## 18. Scheduler

Enable scheduling using the project's existing Spring configuration.

Create a dedicated scheduler wrapper:

```java
@Component
@ConditionalOnProperty(
    prefix = "greenhouse.evaluation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GreenhouseEvaluationScheduler {
    ...
}
```

Use configuration-driven timing.

A possible implementation is:

```java
@Scheduled(
    fixedDelayString = "${greenhouse.evaluation.interval:PT1M}",
    initialDelayString = "${greenhouse.evaluation.initial-delay:PT10S}"
)
public void reconcile() {
    coordinator.evaluate();
}
```

Confirm that the current Spring version supports ISO-8601 duration strings in these attributes.

If it does not, use a scheduling configuration compatible with the project version rather than converting values informally.

### 18.1 Fixed delay versus fixed rate

Use fixed delay.

This prevents overlapping executions if an evaluation takes longer than expected.

```text
Evaluation completes
    ↓
Wait configured interval
    ↓
Start next evaluation
```

### 18.2 Concurrency guard

Prevent overlapping runs.

At minimum, use an in-process lock such as an `AtomicBoolean`.

Behaviour:

```text
Evaluation already running
→ skip this invocation
→ log warning
```

Do not add a distributed lock library in v1.

### 18.3 Failure behaviour

If one scheduled evaluation fails:

- log the exception
- do not terminate the scheduler
- allow the next scheduled cycle to retry naturally
- do not partially persist reconciliation changes

The scheduler must catch top-level exceptions after transactional rollback.

### 18.4 Startup

The first run should occur after a short configurable initial delay so the application can complete startup.

Default:

```text
PT10S
```

---

## 19. APIs

### 19.1 GET `/api/v1/assessments`

Return assessments.

Suggested query parameters:

```text
status
greenhouseId
scopeType
scopeId
code
```

For v1, support at least:

```http
GET /api/v1/assessments
GET /api/v1/assessments?status=ACTIVE
```

Default behaviour should return active assessments only, ordered by:

1. severity descending
2. first detected time ascending

Avoid returning unlimited historical data.

If historical records are included, use pagination.

Suggested response:

```json
{
  "generatedAt": "2026-07-26T06:30:00Z",
  "assessments": [
    {
      "id": 42,
      "correlationKey": "greenhouse-01:ZONE:zone-main:TEMPERATURE_ABOVE_LIMIT",
      "greenhouseId": "greenhouse-01",
      "zoneId": "zone-main",
      "deviceId": "bme280-01",
      "scopeType": "ZONE",
      "scopeId": "zone-main",
      "code": "TEMPERATURE_ABOVE_LIMIT",
      "severity": "WARNING",
      "status": "ACTIVE",
      "message": "Zone zone-main temperature of 36.2°C is above the configured maximum of 35.0°C.",
      "evidence": {
        "actualTemperatureCelsius": 36.2,
        "maximumTemperatureCelsius": 35.0
      },
      "ruleId": "temperature-operating-limit",
      "ruleVersion": 1,
      "firstDetectedAt": "2026-07-26T06:24:00Z",
      "lastDetectedAt": "2026-07-26T06:30:00Z",
      "lastEvaluatedAt": "2026-07-26T06:30:00Z",
      "resolvedAt": null
    }
  ]
}
```

Do not expose JPA entities directly.

### 19.2 GET `/api/v1/state`

Create the composed operational API.

Response:

```json
{
  "generatedAt": "2026-07-26T06:30:00Z",
  "twin": {
    "...": "existing facts-only twin response"
  },
  "assessments": [
    {
      "...": "active assessment response"
    }
  ]
}
```

The state endpoint should:

1. Build the current twin.
2. Query currently persisted active assessments.
3. Return them together.

It should not run reconciliation as a side effect.

GET requests must remain read-only.

This means:

```http
GET /api/v1/state
```

does not raise, update or resolve assessments.

The scheduler owns ongoing reconciliation.

### 19.3 Optional manual evaluation endpoint

Do not add a public manual evaluation endpoint unless needed for operations or tests.

If one is added, it should be clearly administrative:

```http
POST /api/v1/admin/evaluations
```

It must call the same coordinator used by the scheduler.

Do not duplicate evaluation logic in the controller.

Prefer leaving it out of v1 unless the existing project has an administration pattern.

---

## 20. API error handling

Use the project's existing error response conventions.

Handle:

- invalid enum query values
- invalid pagination
- missing greenhouse
- unexpected reconciliation errors
- configuration errors at startup

Do not return stack traces.

---

## 21. Clock and deterministic time

Add a shared `Clock` bean if the application does not already have one:

```java
@Bean
Clock clock() {
    return Clock.systemUTC();
}
```

Inject `Clock` into:

- coordinator
- reconciler
- persistence timestamp creation
- state response generation where appropriate

Unit tests should use:

```java
Clock.fixed(...)
```

This is required for reliable lifecycle and boundary tests.

---

## 22. Transaction design

Recommended transaction boundaries:

```text
Observation persistence
→ existing transaction

Twin construction
→ read-only

Assessment rule execution
→ no transaction required

Assessment reconciliation
→ one write transaction

Assessment queries
→ read-only
```

Do not hold a database transaction open while performing unrelated external calls.

There are no external calls in Assessment Engine v1.

---

## 23. Logging

Use structured, concise logging.

### 23.1 Scheduler start and completion

Example:

```text
Starting greenhouse evaluation greenhouseId=greenhouse-01 evaluatedAt=...
```

Completion:

```text
Completed greenhouse evaluation greenhouseId=greenhouse-01 raised=1 updated=0 resolved=0 durationMs=...
```

### 23.2 Assessment lifecycle

Log at `INFO` when an assessment is:

- raised
- resolved
- severity changed

Do not log every unchanged active assessment at `INFO`.

Use `DEBUG` if detailed cycle-level evidence is useful.

### 23.3 Failures

Log:

- evaluation timestamp
- greenhouse ID if available
- exception
- current scheduler invocation

Do not log full sensitive payloads unnecessarily.

---

## 24. Metrics

If Spring Boot Actuator and Micrometer are already present, add counters and timers using existing conventions.

Suggested metrics:

```text
greenhouse.evaluation.runs
greenhouse.evaluation.failures
greenhouse.evaluation.duration
greenhouse.assessment.raised
greenhouse.assessment.resolved
greenhouse.assessment.active
```

Do not add a new monitoring dependency solely for this milestone.

---

## 25. Testing requirements

All existing tests must continue to pass.

Add comprehensive unit and integration tests.

### 25.1 Temperature rule tests

Test:

```text
below minimum
equal to minimum
inside range
equal to maximum
above maximum
missing temperature
offline source
```

Confirm exact boundaries remain normal.

### 25.2 Humidity rule tests

Test:

```text
below minimum
equal to minimum
inside range
equal to maximum
above maximum
missing humidity
offline source
```

### 25.3 Freshness rule tests

Test all current twin freshness states and exact timing boundaries.

Examples:

```text
one instant before stale threshold
exactly at stale threshold
one instant after stale threshold
```

Use the established Digital Twin v1 semantics rather than inventing new semantics.

### 25.4 Device availability rule tests

Test:

```text
ONLINE
DELAYED
OFFLINE
exact offline boundary
```

### 25.5 Reconciler tests

Test:

#### New condition

```text
No active record
Finding exists
→ one ACTIVE assessment raised
```

#### Persistent condition

```text
Active record exists
Matching finding exists
→ same record updated
→ no duplicate
```

#### Resolved condition

```text
Active record exists
No current finding
→ record resolved
```

#### Recurrence

```text
Resolved historical record exists
Finding occurs again
→ new active record created
```

#### Multiple findings

```text
Independent findings
→ independent assessment records
```

#### Duplicate finding keys

```text
Two findings have the same correlation key
→ evaluation fails clearly
```

#### Severity change

```text
Existing active finding changes severity
→ same assessment updated
```

### 25.6 Repository integration tests

Using the project's existing PostgreSQL test strategy, verify:

- insert active assessment
- partial unique index prevents duplicate active correlation keys
- resolved assessment permits later new active record
- JSONB evidence round trips correctly
- enum mappings work
- queries return the correct status and order

Do not replace PostgreSQL-specific integration testing with H2 if the project currently tests PostgreSQL behaviour.

### 25.7 Scheduler tests

Test:

- scheduler calls coordinator when enabled
- scheduler is disabled through configuration
- coordinator exception is caught and logged
- subsequent invocations remain possible
- overlapping evaluation is prevented

Avoid tests that wait for a real minute.

Invoke the scheduled method directly or use a short test configuration.

### 25.8 API integration tests

Test:

```http
GET /api/v1/assessments
GET /api/v1/assessments?status=ACTIVE
GET /api/v1/state
GET /api/v1/twin
```

Verify:

- twin no longer contains assessment warning semantics
- state contains twin plus active assessments
- resolved assessments are excluded from the default state response
- GET requests do not modify assessment records
- response timestamps and fields are correct

### 25.9 End-to-end lifecycle test

Create an integration test demonstrating:

```text
1. Insert a high-temperature observation.
2. Run coordinator.
3. Verify TEMPERATURE_ABOVE_LIMIT is ACTIVE.
4. Run coordinator again with the same state.
5. Verify there is still only one active assessment.
6. Insert a normal-temperature observation.
7. Run coordinator.
8. Verify the assessment is RESOLVED.
9. Verify /api/v1/state contains no active high-temperature assessment.
```

---

## 26. Migration from current twin warnings

The migration must be handled deliberately.

### Step 1

Identify all warning and environmental assessment logic in:

```text
TwinAssembler
TwinService
Twin response records
Twin tests
```

### Step 2

Recreate equivalent rules under:

```text
com.greenhouse.assessment.rule
```

### Step 3

Add rule tests before removing the old logic.

### Step 4

Remove the assessment logic from the twin.

### Step 5

Update `/api/v1/twin` tests to confirm facts-only behaviour.

### Step 6

Create `/api/v1/state` to restore the complete operational view.

### Step 7

Run regression tests against exact thresholds and freshness boundaries.

There must be one authoritative implementation of each rule.

Do not leave old and new rule implementations running in parallel.

---

## 27. Suggested implementation order

Implement in this order:

1. Inspect repository and document current twin warning logic.
2. Run and record baseline tests.
3. Add `Clock` abstraction if absent.
4. Add assessment enums and domain records.
5. Add Flyway assessment-table migration.
6. Add entity, repository and mapper.
7. Implement correlation-key factory.
8. Implement rule interface.
9. Implement temperature rule.
10. Implement humidity rule.
11. Implement freshness rule.
12. Implement device availability rule.
13. Add rule unit tests.
14. Implement assessment reconciler.
15. Add reconciliation tests.
16. Implement Assessment Service.
17. Refactor Digital Twin to facts-only.
18. Add `/api/v1/assessments`.
19. Add `/api/v1/state`.
20. Implement evaluation coordinator.
21. Implement one-minute scheduler.
22. Add scheduler and integration tests.
23. Run complete test suite.
24. Build the deployable JAR.
25. Verify locally.
26. Deploy to the Raspberry Pi using the established deployment process.
27. Verify live APIs and scheduler behaviour.
28. Update architecture documentation.
29. Commit the completed milestone.

---

## 28. Acceptance criteria

The implementation is accepted when all of the following are true.

### Digital Twin

- `GET /api/v1/twin` returns factual greenhouse state.
- Environmental warning creation no longer occurs inside the twin.
- Existing freshness and status boundary semantics remain correct.
- Twin construction remains request-driven and reusable by the scheduler.

### Assessment Engine

- Assessment rules consume `GreenhouseTwin`.
- Temperature, humidity, freshness and device availability rules are implemented.
- Boundary values behave exactly as specified.
- Missing environmental values do not create false assessments.
- Assessment rules are stateless and independently tested.

### Persistence

- Assessments are stored in PostgreSQL.
- One logical condition creates one active assessment.
- Persistent conditions update the same record.
- Conditions that return to normal resolve the active assessment.
- Repeated future occurrences create new historical records.
- Duplicate active assessments are prevented at database level.

### Runtime

- The coordinator evaluates the greenhouse automatically.
- Evaluation runs approximately every minute using fixed delay.
- Scheduling is configuration-driven.
- Failed evaluations are retried naturally on the next cycle.
- Evaluation runs cannot overlap inside one application instance.

### APIs

- `GET /api/v1/assessments` returns assessments.
- Active assessments are the default query result.
- `GET /api/v1/state` returns the current twin plus active assessments.
- GET requests do not perform reconciliation or persistence.
- API responses do not expose JPA entities.

### Testing

- Existing tests pass.
- New rule tests pass.
- Reconciliation lifecycle tests pass.
- PostgreSQL integration tests pass.
- API integration tests pass.
- End-to-end assessment lifecycle test passes.

---

## 29. Definition of Done

The milestone is complete when:

- implementation is merged into the current codebase
- Flyway migration applies successfully
- complete test suite passes
- application builds successfully
- application starts successfully on the Raspberry Pi
- scheduled evaluation runs are visible in logs
- live high or low test conditions can raise an assessment
- normal conditions resolve an assessment
- `/api/v1/twin` is facts-only
- `/api/v1/assessments` works
- `/api/v1/state` works
- project documentation is updated
- no Kafka, event broker or Decision Engine code has been introduced

---

## 30. Live verification checklist

After deployment, verify:

### Health and startup

```text
Application starts without configuration-validation errors.
Flyway migration succeeds.
Scheduler starts after configured initial delay.
```

### Twin

```http
GET /api/v1/twin
```

Confirm:

- latest readings are present
- device status is present
- freshness is present
- old warning fields are absent or formally deprecated
- no assessment persistence occurs from the GET request

### Assessments

```http
GET /api/v1/assessments?status=ACTIVE
```

Confirm normal conditions produce no false assessment.

Create or simulate a threshold breach using the safest existing test mechanism.

Confirm:

- one assessment becomes active
- repeated evaluations do not create duplicate active records
- `lastEvaluatedAt` advances

Restore normal conditions.

Confirm:

- assessment becomes resolved
- active endpoint no longer returns it
- historical query can retrieve it if implemented

### State

```http
GET /api/v1/state
```

Confirm it contains:

```text
twin
assessments
generatedAt
```

### Database

Using PostgreSQL CLI, verify:

```sql
SELECT
    id,
    correlation_key,
    code,
    severity,
    status,
    first_detected_at,
    last_evaluated_at,
    resolved_at
FROM assessment
ORDER BY id DESC;
```

---

## 31. Documentation updates

Update architecture documentation to show:

```text
Observation Domain
    ↓
Digital Twin
    ↓
Assessment Engine
    ↓
Persisted Assessments
```

Document that:

- the twin owns facts
- assessments own interpretation
- the scheduler runs a one-minute reconciliation loop
- `/api/v1/state` composes facts and assessments
- events and Kafka are deferred
- Decision Engine is the next planned domain

Add or update an ADR covering:

```text
Use scheduled reconciliation for Assessment Engine v1
```

The ADR should record:

### Decision

Use an internal Spring scheduled reconciliation loop with PostgreSQL-persisted assessments.

### Rationale

- current observation frequency is approximately one minute
- current deployment is a single Spring Boot application on a Raspberry Pi
- scheduled reconciliation detects both new conditions and missing observations
- it naturally recovers after application restarts
- formal event infrastructure would add unnecessary complexity

### Consequences

- assessment latency can be up to one evaluation interval
- reconciliation code must be idempotent
- future event-driven triggers can be added without replacing the assessment domain
- Kafka remains a later scaling option

---

## 32. Instructions to Claude Code

1. Inspect the actual repository before making changes.
2. Do not invent parallel abstractions where equivalent classes already exist.
3. Follow existing formatting, package, API and testing conventions.
4. Present a concise implementation plan before editing.
5. Identify all current twin warning logic before moving it.
6. Preserve all exact freshness and threshold boundary semantics.
7. Add tests before or alongside each material change.
8. Use Flyway for schema changes.
9. Use PostgreSQL-compatible mappings and tests.
10. Keep the Digital Twin facts-only.
11. Keep rules deterministic and stateless.
12. Keep orchestration outside domain packages.
13. Do not introduce Kafka, MQTT, agents or microservices.
14. Do not implement the Decision Engine.
15. Run the complete test suite before completion.
16. Report:
    - files added
    - files changed
    - migrations added
    - APIs changed
    - tests added
    - test results
    - deployment verification
    - any deviations from this specification

---

## 33. Target milestone statement

The completed milestone should be describable as:

> Assessment Engine v1 evaluates the facts-only Digital Twin through a configurable one-minute reconciliation loop. It applies deterministic environmental, freshness and device-health rules, persists active and resolved assessment lifecycles in PostgreSQL, and exposes assessments separately and through a composed greenhouse-state API.
