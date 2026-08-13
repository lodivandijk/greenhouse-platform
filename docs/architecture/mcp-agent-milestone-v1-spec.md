# Greenhouse Platform — MCP Agent Milestone Build Specification

## 1. Purpose

This specification defines the next implementation milestone for the Greenhouse Platform.

The starting point is the current deployed platform, which already has:

* ESP32 sensor ingestion
* environmental observations persisted to PostgreSQL
* device/heartbeat handling
* facts-only Digital Twin
* deterministic Assessment Engine
* GET /api/v1/twin
* GET /api/v1/state
* existing REST APIs
* Spring Boot running as a single application on the Raspberry Pi
* existing remote access through Tailscale

The target of this milestone is to make the greenhouse usable from a fresh Claude Code session through MCP.

At completion, a Claude instance with no knowledge of the source repository or previous design discussions should be able to connect to the running greenhouse platform and:

1. inspect the greenhouse,
2. create and inspect crops,
3. record high-level crop goals,
4. record harvests,
5. record manual crop observations,
6. retrieve crop history,
7. combine crop information with current greenhouse state.

This milestone does not implement autonomous loops, actuator control, crop strategies, optimisation, experimentation or automatic greenhouse management.

---

## 2. Architectural Intent

This milestone is the first practical step toward an AI-first greenhouse architecture.

The operating model becomes:

```
User
 │
 ▼
Claude / GPT
 │
 │ MCP
 ▼
┌────────────────────────────────────────────┐
│          greenhouse.jar                   │
│          Spring Boot / one JVM            │
│                                            │
│ Existing                                  │
│ ├── device                                │
│ ├── observation                           │
│ ├── twin                                  │
│ └── assessment                            │
│                                            │
│ New                                       │
│ ├── crop                                  │
│ ├── goal                                  │
│ └── mcp                                   │
│                                            │
└──────────────────────┬─────────────────────┘
                       │
                       ▼
                   PostgreSQL
```

The important architectural rule is:

Add an agent capability layer to the existing platform. Do not rebuild the platform around AI.

---

## 3. Non-Negotiable Architecture Constraints

The implementation must preserve the existing architecture unless a change is essential to this milestone.

Do not:

* replace the current environmental Observation model,
* modify the Digital Twin into an AI/domain model,
* move assessments into AI,
* create microservices,
* introduce another database,
* introduce Neo4j or another graph database,
* create a generic entity / relationship persistence framework,
* implement the Loop Runtime,
* implement autonomous decision making,
* implement actuators,
* introduce a generic workflow engine,
* introduce crop-specific optimisation rules,
* build a new UI,
* make the existing telemetry path depend on MCP or an LLM.

The existing application remains a modular monolith running in one JVM.

---

## 4. Current System — Preserve

The existing system should continue to operate unchanged:

```
ESP32
  │
  ▼
Observation ingestion
  │
  ▼
PostgreSQL
  │
  ├──────────────► Digital Twin
  │                    │
  │                    ▼
  │              Assessment Engine
  │                    │
  │                    ▼
  └──────────────► /api/v1/state
```

The current machine observation model remains the telemetry source of truth.

Do not generalise machine telemetry into the new crop-observation model.

There will deliberately be two different concepts:

```
Environmental Observation
─────────────────────────
Machine-generated telemetry
temperature
humidity
pressure
etc.
Crop Observation
────────────────
Biological / semantic evidence
plant health
flowering
woodiness
quality
taste
etc.
```

This separation is intentional.

---

## 5. Milestone Outcome

At the end of this milestone, a new empty directory on another machine should be sufficient to operate the crop-recording parts of the greenhouse:

```
greenhouse-agent/
├── CLAUDE.md
└── .mcp.json
```

There should be no greenhouse Java source code in this directory.

Claude Code should connect to:

```
Mac / client
     │
     │ Tailscale
     ▼
Raspberry Pi
     │
     ▼
greenhouse.jar
     │
     ├── MCP endpoint
     ├── existing services
     └── PostgreSQL
```

This clean client is the key acceptance boundary.

---

## 6. New Domain Module: Crop

Create a new package:

```
com.greenhouse.crop
```

Introduce a persisted Crop domain entity.

The intent is simple:

Represent what is physically being grown in the greenhouse.

Suggested model:

```
Crop

id
species
variety
locationId
plantedAt
endedAt
status
notes
createdAt
updatedAt
```

Suggested status enum:

```
PLANNED
ESTABLISHING
PRODUCTIVE
DECLINING
ENDED
```

Do not introduce detailed botanical taxonomy.

Do not introduce species/variety master-data tables unless existing implementation conventions strongly justify them.

For the initial implementation, strings are acceptable for species and variety.

---

## 7. Crop Location

The crop needs a location.

Use the current greenhouse/domain configuration where possible.

Do not introduce an entire generic spatial graph.

The implementation should support identifying where a crop is growing using a stable identifier such as:

```
planter-02
bed-01
zone-main
```

If there is not yet a persisted Planter model, avoid inventing a large one solely for this milestone.

A simple validated/string location reference is acceptable initially.

Document the decision.

---

## 8. New Domain Concept: Goal

Create package:

```
com.greenhouse.goal
```

A Goal represents the user's desired outcome for a crop.

It is deliberately higher-level than an executable objective.

Examples might eventually include:

```
MAXIMISE_LONG_TERM_YIELD
MAXIMISE_FOLIAGE
MAXIMISE_FLOWERING
MAXIMISE_FRUIT_QUALITY
```

However, the implementation should not hard-code horticultural behaviour for these goal types.

Suggested model:

```
Goal
id
cropId
goalType
description
status
priority
sourceInstruction
createdAt
updatedAt
metadata
```

Suggested status:

```
ACTIVE
COMPLETED
CANCELLED
```

sourceInstruction should retain the user's original natural-language intent where supplied.

Example:

```
sourceInstruction:
"I want as much usable foliage as possible for as long as possible."
```

This provides provenance.

---

## 9. Goal Type Flexibility

Do not attempt to define every possible crop goal now.

Prefer either:

* a small enum with an OTHER/custom description capability, or
* a validated string-based type if that better matches existing conventions.

The architecture must allow future goals without database redesign.

Goal does not contain executable environmental controls at this stage.

---

## 10. New Domain Concept: Harvest

Add Harvest under the crop domain.

Suggested model:

```
Harvest
id
cropId
harvestedAt
quantity
unit
notes
createdAt
```

Initial quantity support should be suitable for:

```
g
kg
count
```

Do not build a universal units framework unless one already exists.

The important thing is to preserve:

```
value + unit
```

rather than storing ambiguous raw numbers.

Harvest is a first-class event because it will later become a major outcome signal.

---

## 11. New Domain Concept: CropObservation

Add a separate CropObservation.

This must not replace or modify the existing environmental Observation.

Suggested model:

```
CropObservation
id
cropId
metric
valueType
numericValue
textValue
booleanValue
unit
source
confidence
observedAt
notes
createdAt
metadata
```

The exact implementation can be simplified if appropriate, but the model must support different kinds of semantic observations.

Examples:

```
STEM_WOODINESS = MODERATE
FLOWER_COUNT = 12
PLANT_HEALTH = HEALTHY
SWEETNESS_SCORE = 8
BRIX = 7.9
```

---

## 12. Crop Observation Sources

Observation provenance is important.

Support a source concept such as:

```
HUMAN
AI_DERIVED
DERIVED
EXTERNAL
```

HUMAN is the primary source required for this milestone.

Future camera or calculated observations must be possible without changing the core model.

---

## 13. Flexible Observation Metrics

Do not add database columns for individual crop characteristics.

Bad:

```
crop_observation.stem_woodiness
crop_observation.flower_count
crop_observation.sweetness
```

Instead use:

```
metric = STEM_WOODINESS
metric = FLOWER_COUNT
metric = SWEETNESS_SCORE
```

Architectural rule:

Strict observation envelope, flexible metric vocabulary.

A metric should always have:

```
crop
metric
value
source
timestamp
```

plus:

```
unit
confidence
notes
```

where relevant.

For this milestone, the metric registry may be a Java enum.

Do not build a database-driven metric-definition subsystem unless needed.

---

## 14. Persistence

Use PostgreSQL.

Use the project's existing database migration mechanism and conventions.

Expected new persistence concepts:

```
crop
goal
harvest
crop_observation
```

No additional databases.

No graph persistence.

No vector database.

No AI memory store.

---

## 15. Crop History

Implement a domain/service operation capable of returning a useful crop history.

Conceptually:

```
getCropHistory(cropId)
```

The history should combine, at minimum:

```
Crop
Goal(s)
Harvest(s)
CropObservation(s)
```

Where practical, it should also expose the relevant environmental context through existing services, but do not denormalise the existing telemetry into the crop tables.

The implementation may aggregate this in a service DTO.

Example conceptual response:

```
Crop
  ↓
Goals
  ↓
Timeline
  ├── planted
  ├── observation
  ├── harvest
  ├── observation
  └── harvest
```

---

## 16. Greenhouse Context

The agent must be able to ask:

```
What is happening in the greenhouse now?
```

Reuse the existing Digital Twin / state composition rather than building new logic.

The MCP layer should expose the existing trusted state.

Do not recreate state from raw telemetry inside MCP.

---

## 17. MCP Server

Add MCP support to the existing Spring Boot application.

Suggested package:

```
com.greenhouse.mcp
```

The MCP server must run inside the same Spring Boot process.

Conceptual deployment:

```
greenhouse.jar
├── REST APIs
└── MCP endpoint
```

Do not deploy a second MCP process unless the framework makes in-process hosting genuinely impossible.

If implementation requires deviation, document the reason before proceeding.

---

## 18. MCP Transport

Prefer a network-accessible HTTP MCP transport suitable for use over Tailscale.

Target conceptual endpoint:

```
http://<pi-tailscale-host>:<port>/mcp
```

The exact endpoint depends on the selected Spring MCP implementation.

Do not hard-code an IP address into application code.

Endpoint configuration should use application configuration/environment settings where appropriate.

---

## 19. MCP Authentication

The MCP endpoint must not be intentionally exposed anonymously.

For this milestone, simple bearer-token authentication is acceptable.

Desired conceptual request:

```
Authorization: Bearer <token>
```

Requirements:

* secret must not be committed to Git,
* configuration must support Pi deployment,
* documentation must explain how the client supplies it,
* existing REST behaviour must not accidentally be broken.

Do not introduce a full OAuth system in this milestone.

---

## 20. Initial MCP Tool Surface

Expose a deliberately small tool set.

Read tools

```
get_greenhouse_state
list_crops
get_crop
get_crop_history
list_goals
```

Write tools

```
create_crop
update_crop
create_goal
record_harvest
record_crop_observation
```

The exact naming may be adjusted to framework conventions, but semantic intent should remain clear.

---

## 21. MCP Tool: get_greenhouse_state

This should reuse the trusted existing state/twin/assessment functionality.

Do not create parallel calculations.

The result should be concise enough for an LLM to consume.

Return relevant fields such as:

```
greenhouse identity
current environmental readings
freshness/connectivity
active assessments
timestamp
```

---

## 22. MCP Tool: list_crops

Return current crops, preferably with enough information for disambiguation:

```
id
species
variety
location
status
plantedAt
```

Do not return unnecessary persistence internals.

---

## 23. MCP Tool: get_crop

Return one crop plus its current Goal summary where useful.

Do not automatically return enormous observation histories.

Detailed history belongs in get_crop_history.

---

## 24. MCP Tool: create_crop

Accept fields such as:

```
species
variety
location
plantedAt
notes
```

Validate input through the crop domain service.

The MCP implementation must not write directly through repositories.

Flow:

```
MCP Tool
   ↓
CropService
   ↓
Repository
```

---

## 25. MCP Tool: create_goal

Accept:

```
cropId
goalType
description
sourceInstruction
```

The Goal must reference a valid crop.

Do not automatically turn Goals into Objectives.

That comes in a later milestone.

---

## 26. MCP Tool: record_harvest

Accept:

```
cropId
quantity
unit
harvestedAt
notes
```

Return enough data to confirm what was recorded.

---

## 27. MCP Tool: record_crop_observation

Accept:

```
cropId
metric
value
unit
source
confidence
observedAt
notes
```

The interface should make the supported value formats clear to the LLM.

If the framework's tool schema makes multiple value types awkward, choose a simple but explicit representation and document it.

Avoid opaque arbitrary JSON as the primary value model.

---

## 28. MCP Tool: get_crop_history

This is a key agent tool.

Return a structured history including:

```
crop metadata
goals
harvests
crop observations
relevant summary values
```

Environmental history may be added selectively if there is already a clean service for it.

Do not return every minute-level sensor observation by default.

The LLM should not receive hundreds of thousands of telemetry rows.

Future tools can expose bounded environmental ranges.

---

## 29. Tool Design Principles

Every MCP tool must:

1. have one clear purpose,
2. use domain services,
3. validate inputs,
4. return structured output,
5. expose domain language rather than database language,
6. avoid raw SQL concepts,
7. avoid leaking JPA/internal implementation details,
8. produce useful errors for an AI client.

For example:

Good:

```
Crop not found: basil-001
```

Bad:

```
JpaObjectRetrievalFailureException...
```

---

## 30. Explicitly Forbidden MCP Tools

Do not expose:

```
execute_sql
query_database
run_shell
write_file
execute_gpio
raw_repository_access
```

Do not expose unrestricted Spring endpoints as MCP tools automatically.

Tool exposure must be deliberate.

---

## 31. Agent Instructions / Harness Seed

Create a small example agent instruction file for use from the clean Claude Code test repository.

Suggested content:

```
You are my greenhouse assistant.
Use connected greenhouse MCP tools as the source of truth for:
- current greenhouse state
- crops
- goals
- crop observations
- harvest history
Do not invent greenhouse-specific facts if they are available through tools.
When I describe a real event such as planting, harvesting or a crop observation, use the appropriate greenhouse tool to record it when my intent is clear.
Distinguish general horticultural knowledge from evidence recorded from this greenhouse.
A Goal represents the outcome I want from a crop. Do not invent automated control objectives or claim the greenhouse can perform physical actions that are not exposed as tools.
When uncertain whether I am describing a real event or discussing a hypothetical scenario, ask before persisting data.
```

This may be supplied as:

```
CLAUDE.example.md
```

or in the MCP setup documentation.

Do not put implementation details in the agent instructions.

---

## 32. Clean-Agent Test Repository

The milestone must include documentation showing how to create a clean test environment.

Example:

```
mkdir greenhouse-agent
cd greenhouse-agent
git init
```

The test repository should need only:

```
CLAUDE.md
.mcp.json
```

or equivalent local Claude Code MCP configuration.

It should not need access to the greenhouse source repository.

---

## 33. MCP Client Setup Documentation

Create:

```
docs/mcp/AGENT_SETUP.md
```

This document must contain:

* MCP endpoint format,
* transport type,
* authentication setup,
* Pi/Tailscale connectivity prerequisites,
* exact Claude Code configuration command OR .mcp.json,
* how to verify MCP connectivity,
* how to list/discover available tools,
* expected tool list,
* common failure modes,
* how to start a clean agent test repo.

The document should be written for someone who does not need to know the Java implementation.

---

## 34. Development Documentation

Also document:

```
docs/mcp/IMPLEMENTATION.md
```

Include:

* selected Spring MCP library/framework,
* MCP server configuration,
* package structure,
* tool registration approach,
* authentication mechanism,
* mapping from MCP tools to domain services,
* known limitations,
* future extension points.

Keep this separate from AGENT_SETUP.md.

---

## 35. API Surface

Do not assume MCP replaces REST.

REST remains useful for:

```
testing
debugging
existing UI
future integrations
```

For new crop concepts, either:

* expose normal REST endpoints as well, or
* at minimum ensure the domain services are independent of MCP.

Preferred layering:

```
           ┌──── REST Controller
           │
Domain Service
           │
           └──── MCP Tool
```

not:

```
MCP → REST → service
```

and not:

```
REST → MCP → service
```

Both adapters should call the domain service directly.

---

## 36. Suggested Package Structure

Do not reorganise existing code unnecessarily.

Suggested additive structure:

```
com.greenhouse
├── existing...
│
├── crop
│   ├── Crop
│   ├── CropStatus
│   ├── CropRepository
│   ├── CropService
│   ├── CropController
│   │
│   ├── CropObservation
│   ├── CropObservationMetric
│   ├── CropObservationSource
│   ├── CropObservationRepository
│   ├── CropObservationService
│   │
│   ├── Harvest
│   ├── HarvestRepository
│   └── HarvestService
│
├── goal
│   ├── Goal
│   ├── GoalType
│   ├── GoalStatus
│   ├── GoalRepository
│   └── GoalService
│
└── mcp
    ├── GreenhouseStateTools
    ├── CropTools
    ├── GoalTools
    └── ...
```

Exact file structure may be adapted to current project conventions.

---

## 37. Testing Requirements

Domain tests

Cover:

```
create crop
update crop
end crop
invalid crop
create goal
invalid crop goal
record harvest
record crop observation
retrieve crop history
```

Repository tests

Cover persistence and ordering.

Harvests and observations should return in predictable chronological order.

MCP contract tests

Test:

```
tool discovery
tool schema
valid invocation
invalid invocation
domain error mapping
authentication
```

Existing regression tests

All existing:

```
observation
twin
assessment
state
```

tests must remain green.

---

## 38. Live Deployment Test

Deploy the updated application to the Pi using the existing deployment approach.

Verify:

```
existing sensor ingestion continues
observations continue writing
Twin still works
Assessment Engine still works
/api/v1/state still works
MCP endpoint is reachable over Tailscale
authentication works
```

Do not consider the milestone complete with only local unit tests.

---

## 39. Clean Claude Code Acceptance Test

This is the primary milestone acceptance test.

Start Claude Code from a clean repo with no greenhouse source code or project history.

Claude should connect only through MCP.

Test 1 — Discovery

Ask:

```
What do you know about my greenhouse?
```

Expected:

* Claude invokes get_greenhouse_state.
* It accurately reports real current state.
* It does not invent crops.

Test 2 — Create Crop

Say:

```
I am planting a new crop in planter 2.
```

Provide whatever minimum crop identity the agent reasonably needs.

Expected:

* Claude recognises this as a real greenhouse event.
* Claude invokes create_crop.
* Crop persists.
* Subsequent list_crops returns it.

Do not test crop-specific horticultural intelligence in this milestone.

Test 3 — Create Goal

Say:

```
My goal for this crop is to maximise its useful output over as long a productive period as possible.
```

Expected:

* Claude creates an appropriate Goal or uses a sufficiently general goal representation.
* Original user intent is preserved.
* Claude does not create autonomous objectives or pretend it can control unsupported hardware.

Test 4 — Record Harvest

Say:

```
I harvested 180g today.
```

Expected:

* Claude resolves the active crop if unambiguous.
* record_harvest is called.
* 180g is persisted.

Test 5 — Record Semantic Observation

Say:

```
The plant still looks healthy, but the older stems are getting woody.
```

Expected:

* Claude records appropriate crop observations using the available metric vocabulary.
* It does not modify environmental telemetry.

Test 6 — Retrieve History

Ask:

```
Summarise everything we know about this crop so far.
```

Expected:

Claude retrieves structured crop history and can report:

```
identity
location
goal
harvest history
manual observations
current greenhouse environment
```

without requiring access to the source repository.

---

## 40. Success Criterion

The milestone succeeds when this is true:

A fresh Claude Code session that knows nothing about the implementation can correctly understand and manipulate greenhouse crop records using only the MCP capabilities exposed by the running Spring application.

This is more important than the internal elegance of the MCP implementation.

If the clean agent needs detailed database/schema/package knowledge to use the system effectively, the tool model needs improvement.

---

## 41. Scope Explicitly Deferred

Do not implement these in this milestone:

```
Objective
Loop Runtime
Decision
Command
Outcome
Strategy domain model
Strategy versioning
Automated crop planning
Automated harvest recommendations
Irrigation control
Heating control
Ventilation control
Camera analysis
AI vision
Knowledge graph
Graph database
Experiment framework
Optimisation algorithms
Crop-specific rules
Agent-generated scheduled jobs
View rendering framework
```

These remain architectural directions, not current requirements.

---

## 42. Suggested ADRs

Create the following ADRs if equivalent decisions are not already recorded.

ADR — MCP as the Agent Capability Boundary

Context

Claude/GPT requires access to greenhouse capabilities without direct knowledge of databases, source code or hardware implementation.

Decision

Expose selected greenhouse domain capabilities through MCP.

Consequences

* agents depend on tool contracts rather than implementation details,
* tools must be explicitly curated,
* raw database access is prohibited,
* MCP can support multiple future AI clients.

ADR — MCP Hosted Inside Existing Spring Boot Runtime

Context

The platform currently runs as one Spring Boot application on the Raspberry Pi.

Decision

Host MCP inside the existing application rather than creating another deployed service.

Consequences

* one deployment artifact remains,
* domain service reuse is direct,
* no new distributed-system boundary is introduced.

ADR — Crop Domain Added Without Reworking Telemetry

Context

Crop-level semantic information differs materially from high-volume environmental telemetry.

Decision

Add Crop, Harvest and CropObservation while preserving the existing environmental Observation model.

Consequences

* existing telemetry remains stable,
* biological observations can evolve independently,
* some conceptual duplication around the word "observation" is accepted deliberately.

ADR — Goal Represents Intent, Not Executable Control

Context

Users naturally express outcomes that cannot directly be controlled.

Decision

Persist high-level crop intent as Goal without converting it automatically into runtime control behaviour.

Consequences

* vague goals can be stored safely,
* executable Objective/Loop concepts remain deferred,
* future AI reasoning can decompose Goals later.

ADR — Flexible Crop Observation Metrics

Context

Different crops and goals require different human and biological observations.

Decision

Use a stable CropObservation envelope with an extensible metric vocabulary.

Consequences

* new observation types do not require schema redesign,
* metrics remain typed/controlled,
* arbitrary JSON-only observations are avoided.

ADR — PostgreSQL Remains the Only Application Database

Decision

Store all new milestone data in the existing PostgreSQL instance.

Consequences

No graph, vector or document database is introduced.

ADR — Graph Remains a Logical Projection

Context

Future AI reasoning benefits from thinking in terms of relationships.

Decision

Do not introduce explicit graph persistence for this milestone.

Use normal domain relationships and present graph-like context through tools/services when required.

ADR — Fresh Agent Session Is an Architectural Test Boundary

Context

A development Claude session already knows extensive implementation context and would not fairly test the MCP capability model.

Decision

Validate MCP using a clean Claude Code repository with no source code or project history.

Consequences

The MCP tool surface must be understandable and sufficient on its own.

---

## 43. Implementation Checkpoints

Implement and verify in small checkpoints.

Checkpoint A — Crop Domain

Implement:

```
Crop
Harvest
CropObservation
Goal
```

Persistence, services and tests.

Deploy and verify existing system remains healthy.

Checkpoint B — Read MCP

Implement:

```
get_greenhouse_state
list_crops
get_crop
get_crop_history
list_goals
```

Add MCP authentication.

Verify from another machine over Tailscale.

Checkpoint C — Write MCP

Implement:

```
create_crop
update_crop
create_goal
record_harvest
record_crop_observation
```

Validate domain boundaries and errors.

Checkpoint D — Clean Agent Integration

Create:

```
docs/mcp/AGENT_SETUP.md
```

Use a completely separate empty repository to configure Claude Code.

Run the acceptance conversation.

Do not use the development Claude Code session for this test.

Checkpoint E — Deploy / Document

Deploy the final build to the Pi.

Verify:

```
telemetry
Twin
Assessment
REST
MCP
PostgreSQL
Tailscale client access
```

Commit and push only after live verification.

---

## 44. Required Handoff at Completion

Claude Code should report:

1. files/modules added,
2. database migrations added,
3. MCP framework/library selected,
4. endpoint and transport,
5. authentication mechanism,
6. complete MCP tool inventory,
7. unit/integration/MCP test results,
8. Pi deployment verification,
9. clean-agent acceptance-test results,
10. commits created,
11. ADRs added,
12. known limitations,
13. exact instructions for starting a new clean Claude Code agent.

---

## 45. Definition of Done

The milestone is complete only when:

```
✓ Existing observation ingestion works
✓ Digital Twin works
✓ Assessment Engine works
✓ Existing REST state API works
✓ Crop can be persisted
✓ Goal can be persisted
✓ Harvest can be persisted
✓ CropObservation can be persisted
✓ Crop history can be retrieved
✓ MCP server runs in greenhouse.jar
✓ MCP is reachable from Mac over Tailscale
✓ MCP requires authentication
✓ Read tools work
✓ Write tools work
✓ Fresh Claude Code repo can connect
✓ Fresh Claude can inspect greenhouse state
✓ Fresh Claude can create a crop
✓ Fresh Claude can create a goal
✓ Fresh Claude can record a harvest
✓ Fresh Claude can record manual observations
✓ Fresh Claude can summarise the crop history
✓ No Loop Runtime was accidentally introduced
✓ No actuator control was introduced
✓ No graph database was introduced
✓ No existing deterministic architecture was replaced
```

---

## 46. Architectural Position After This Milestone

At completion the platform should be:

```
                    USER
                      │
                      ▼
                Claude / GPT
                      │
                     MCP
                      │
                      ▼
┌─────────────────────────────────────────────┐
│             greenhouse.jar                 │
│                                             │
│ Existing trusted core                       │
│                                             │
│ Sensors → Observation → Twin → Assessment  │
│                                             │
│ New biological context                      │
│                                             │
│ Crop → Goal                                 │
│   │                                         │
│   ├── Harvest                               │
│   └── CropObservation                       │
│                                             │
│ MCP provides controlled access to both      │
└─────────────────────┬───────────────────────┘
                      │
                  PostgreSQL
```

The key new capability is:

The greenhouse can now be operated conversationally as a source of persistent crop knowledge, without the AI client knowing how the greenhouse application is implemented.

The next milestone after this should be designed from actual experience using this agent interface, rather than implementing the Loop Runtime purely from theory.
