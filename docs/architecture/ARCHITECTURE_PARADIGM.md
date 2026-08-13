# Greenhouse Architecture Paradigm

## Purpose

The greenhouse platform is moving toward a documentation and architecture model that clearly separates:

1. what is implemented now;
2. why architectural decisions were made;
3. what is being considered for the future.

The goal is to stop maintaining multiple overlapping architecture documents that quickly become stale or contradictory.

---

## 1. One Authoritative Current-State Document

The authoritative description of the implemented system is:

`docs/architecture/CURRENT_ARCHITECTURE.md`

This document describes only implemented reality.

It should explain:

* current system purpose;
* deployed components;
* domain boundaries;
* data flows;
* persistence;
* APIs;
* runtime behaviour;
* deployment;
* known limitations.

It must not present proposed or speculative architecture as though it already exists.

For exact implementation details, source code and tests remain authoritative.

---

## 2. Architecture Decisions Are Preserved as ADRs

Material architectural decisions are recorded as Architecture Decision Records:

`docs/architecture/decisions/`

For example:

```
ADR-001-persist-observations.md
ADR-002-introduce-digital-twin.md
ADR-003-twin-facts-only.md
ADR-004-separate-assessment-engine.md
ADR-005-compose-application-state.md
```

Each ADR should capture:

```
Context
   ↓
Decision
   ↓
Consequences
   ↓
Related / superseded decisions
```

ADRs are historical records.

Once accepted, an ADR should not be rewritten just because the architecture later changes.

Instead, create another ADR that supersedes it.

This provides an explicit record of how the system evolved.

---

## 3. Current Architecture and Decision History Have Different Roles

```
CURRENT_ARCHITECTURE.md
        │
        └── What does the system look like now?
ADRs
        │
        └── Why does it look like this?
```

Claude Code should normally read the current architecture first and consult relevant ADRs when understanding or changing architectural boundaries.

---

## 4. Current Implemented Architecture

The deployed architecture is currently:

```
Physical Environment
        ↓
     Sensors
        ↓
      ESP32
        ↓
  Observations
        ↓
   PostgreSQL
        ↓
  Digital Twin
        ↓
Assessment Engine
        ↓
Application State
        ↓
       UI
```

The system is currently observational rather than autonomous.

Its effective flow is:

```
SENSE
  ↓
STORE
  ↓
MODEL
  ↓
ASSESS
  ↓
DISPLAY
```

There is currently no completed control loop back into the physical greenhouse.

---

## 5. Important Current Domain Separation

The most important implemented architectural principle is:

**FACTS ≠ INTERPRETATION**

The major responsibilities are therefore:

```
Device / Ingestion
    "What did the hardware send?"
        ↓
Observation
    "What was measured?"
        ↓
Digital Twin
    "What do we currently know to be true?"
        ↓
Assessment
    "What do those facts mean?"
        ↓
Application State
    "What does the application need to present?"
        ↓
UI
    "What does the user need to see?"
```

These boundaries should not be collapsed casually.

---

## 6. The Digital Twin Is Facts-Only

The Digital Twin represents the current known physical state.

Examples:

```
temperature = 22.4°C
humidity = 72.5%
device = online
latest observation = 14:32
```

The Twin should not contain crop-specific judgement.

For example:

```
22.4°C
```

is a Twin fact.

```
22.4°C is ideal for this basil at its current growth stage
```

is interpretation and belongs elsewhere.

This distinction is deliberate because the same physical fact may have different meanings depending on:

* crop;
* variety;
* growth stage;
* objective;
* time;
* previous actions;
* surrounding conditions.

---

## 7. Assessment Is Interpretation

The Assessment Engine interprets factual state.

Conceptually:

```
Twin Facts
    ↓
Assessment Rules / Logic
    ↓
Assessment
```

This allows interpretation to evolve without changing the underlying factual representation of the greenhouse.

---

## 8. `/api/v1/state` Is a Read Model

The UI needs both facts and interpretations.

Instead of putting assessments inside the Digital Twin, the application composes them:

```
Digital Twin
     +
Assessments
     ↓
Application State
```

The application-facing endpoint is:

```
GET /api/v1/state
```

This is a read model, not a new source of truth.

---

## 9. Persistence Captures History

PostgreSQL stores durable observations.

A simplified observation currently looks like:

```
Observation
id
device_id
temperature_celsius
humidity_percent
pressure_hpa
received_at
```

This means the platform retains a historical environmental record rather than only maintaining the latest sensor values.

That history is expected to become important for future reasoning and optimisation.

---

## 10. The Next Architecture Paradigm

The longer-term direction is to evolve from an observational application into an objective-driven feedback system.

The likely conceptual flow is:

```
Objective
    ↓
Current State
    ↓
Reasoning
    ↓
Decision
    ↓
Action
    ↓
Outcome
    ↓
Updated State
    └──────────────→ next reasoning cycle
```

This is different from simply adding an LLM to the existing Spring application.

The LLM should become one reasoning capability inside a larger loop rather than the architecture itself.

---

## 11. Objectives Become Explicit

A future system should not rely on vague prompts such as:

```
"Grow the perfect tomato"
```

Instead, optimisation intent should become explicit system state.

Examples:

```
Maximise basil foliage yield for as long as practical.
Maximise flower production.
Maximise fruit sweetness while maintaining acceptable yield.
Maintain healthy vegetative growth before flowering.
```

Different crops or plants may therefore operate under different objectives.

The objective gives later AI reasoning something concrete against which to evaluate decisions and outcomes.

This capability is not yet implemented.

---

## 12. Observations Will Eventually Become Broader Than Sensor Readings

Today, observations mostly represent structured environmental measurements.

Future observations may include:

```
Environmental
- temperature
- humidity
- pressure
- soil moisture
- light
- nutrient measurements
Plant
- flowering
- leaf colour
- growth rate
- disease signs
- stem condition
Harvest
- mass
- count
- quality
- sweetness
- usable yield
Human
- harvested heavily
- plant becoming woody
- flavour excellent
- leaves becoming smaller
Visual
- photographs
- image-derived plant observations
```

The observation model should retain some flexibility because different crops and objectives require different feedback.

---

## 13. AI Should Operate Over Structured Context

The intended architecture is not:

```
Sensor data
    ↓
Huge prompt
    ↓
LLM
    ↓
Do something
```

Instead:

```
Objective
     +
Current factual state
     +
Relevant observations
     +
Crop knowledge
     +
Previous decisions
     +
Previous outcomes
     +
Available actions
        ↓
   AI Reasoning
        ↓
Structured Decision
```

The AI supplies judgement where flexibility and context are useful.

Deterministic software continues to handle:

* persistence;
* validation;
* APIs;
* safety constraints;
* hardware control;
* scheduling;
* identity;
* state management;
* command execution.

---

## 14. The Loop Is the Central Future Abstraction

The longer-term architecture should be thought about as a set of repeated loops:

```
OBSERVE
   ↓
UNDERSTAND
   ↓
DECIDE
   ↓
ACT
   ↓
MEASURE OUTCOME
   ↓
OBSERVE AGAIN
```

Each loop should have enough durable state that the platform can understand:

```
What were we trying to achieve?
What did we know?
What did we decide?
Why did we decide it?
What action happened?
What happened afterwards?
Did that move us toward the objective?
```

This provides the foundation for actual learning and optimisation.

---

## 15. AI Does Not Replace the Domain Model

A key principle is that increased use of AI does not remove the need for structured entities.

Likely durable concepts include:

```
Objective
Observation
Assessment
Decision
Action / Command
Outcome
```

The LLM may create, interpret, relate, or reason over these entities, but important system state should not exist only inside an LLM conversation.

This makes reasoning:

* persistent;
* inspectable;
* replayable;
* auditable;
* comparable over time.

---

## 16. Architecture Evolution Should Be Incremental

The newer loop and AI-first ideas should not be treated as justification for rebuilding the existing platform.

The current architecture already provides useful foundations:

```
Sensors
Observations
Persistence
Digital Twin
Assessment
APIs
UI
```

Future capabilities should normally be introduced alongside these boundaries.

Conceptually:

```
CURRENT
Sensors
   ↓
Observations
   ↓
Twin
   ↓
Assessment
   ↓
State
   ↓
UI
NEXT
Sensors
   ↓
Observations
   ↓
Twin
   ↓
Assessment
   ↓
Objective + Context
   ↓
Decision
   ↓
Suggested Action
LATER
Sensors
   ↓
Observations
   ↓
Twin
   ↓
Reasoning Loop
   ↓
Decision
   ↓
Command
   ↓
Physical Action
   ↓
Outcome
   └────────────→ learning / next loop
```

---

## 17. Current vs Future Must Remain Explicit

Claude Code should distinguish three categories:

**Current**

Implemented and deployed.

This belongs in:

`CURRENT_ARCHITECTURE.md`

**Decided**

A material architecture decision has been made.

This belongs in an ADR.

It may or may not already be implemented.

**Exploratory**

An idea is being discussed but no architecture decision has yet been made.

Examples currently include parts of:

* objective modelling;
* crop strategies;
* action libraries;
* LLM reasoning;
* persistent decisions;
* outcome modelling;
* automated irrigation;
* autonomous control;
* learning and optimisation.

Exploratory ideas must not silently become implementation assumptions.

---

## 18. Development Rule for Claude Code

Before making a material change:

1. Read `CURRENT_ARCHITECTURE.md`.
2. Identify the domain boundary being changed.
3. Read relevant ADRs.
4. Determine whether the change represents:
   `implementation detail`
   or
   `architecture decision`.
5. If it is architectural:
   create a new ADR.
6. Implement the change.
7. Update `CURRENT_ARCHITECTURE.md` only after the implementation
   represents the new reality.
8. Never rewrite historical ADRs simply to make them agree with the
   latest architecture.

---

## 19. Guiding Architectural Principle

The platform should progressively evolve from:

```
A greenhouse monitoring application
```

toward:

```
A persistent, objective-driven system that observes a physical growing
environment, reasons about it, acts where appropriate, measures the
result, and improves future decisions.
```

AI is expected to become increasingly important in the reasoning and interpretation portions of that architecture.

It should not replace reliable conventional software where deterministic behaviour is more appropriate.

The desired combination is therefore:

```
Structured software
        +
Persistent domain state
        +
Real-world observations
        +
Feedback loops
        +
LLM reasoning where judgement is valuable
```

rather than:

```
Traditional application + chatbot
```

or:

```
LLM controls everything
```

This distinction should guide future architectural decisions.
