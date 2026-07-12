# Greenhouse Platform Architecture

## Purpose

This section describes the architecture of the Greenhouse Platform.

The Architecture Decision Records explain **why** major strategic choices were made. These documents explain **what the resulting system looks like**, how the main components interact, and where responsibilities sit.

The architecture is expected to evolve as the platform moves through four broad stages:

1. A single ESP32 node sending heartbeats.
2. Environmental sensing and historical data collection.
3. Automated control of irrigation and greenhouse conditions.
4. A broader Controlled Environment Agriculture platform supporting multiple growing environments.

The architecture must therefore support the initial domestic greenhouse without becoming tied to that deployment.

## Documents

```text
docs/architecture/
├── README.md
├── system-context.md
├── container-architecture.md
├── component-architecture.md
├── deployment-architecture.md
├── data-flow.md
├── digital-twin-architecture.md
├── domain-model.md
└── architecture-roadmap.md
```

## Architectural Summary

The Greenhouse Platform is a local-first, distributed Controlled Environment Agriculture platform.

ESP32 edge nodes measure and act.

The Spring Boot platform receives observations, owns operational state and coordinates behaviour.

The Digital Twin provides the authoritative representation of the greenhouse.

Historical observations provide evidence.

Automation operates through defined and explainable policies.

AI reasons over the Digital Twin rather than communicating directly with hardware.

The Raspberry Pi hosts the first deployment, while the software remains portable to larger environments.

The architecture intentionally starts with a simple heartbeat but is designed to support the gradual addition of sensors, crops, irrigation, automation, intelligence and commercial operation.
