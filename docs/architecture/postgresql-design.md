# PostgreSQL Design

## Purpose

This document captures the architectural decisions for PostgreSQL within
the Greenhouse Platform.

## Role

PostgreSQL is the **System of Record** for: - Observations - Digital
Twin state - Devices and sensors - Greenhouse topology - Crops and
varieties - Rules and policies - Assessments - Execution history

## Architectural Principles

1.  PostgreSQL is the authoritative source of truth.
2.  Observations are immutable events.
3.  The Digital Twin is derived from observations.
4.  Prefer a relational model.
5.  Use Flyway for every schema change.
6.  Store timestamps in UTC (`TIMESTAMPTZ`).
7.  Push integrity into the database with constraints.
8.  Optimise only when evidence justifies it.

## Deployment

    ESP32
       │
    HTTP
       ▼
    Spring Boot
       │ JDBC
       ▼
    PostgreSQL
       │
    USB SSD (preferred)

## Storage Strategy

One PostgreSQL database should initially contain:

-   observations
-   digital_twin
-   devices
-   sensors
-   crops
-   rules
-   assessments
-   actions

Avoid introducing additional databases until there is a demonstrated
need.

## Future Evolution

Potential future enhancements:

-   TimescaleDB
-   Partitioning
-   Continuous aggregates
-   Replication
-   Cloud backups

These should be introduced only when operational requirements justify
them.
