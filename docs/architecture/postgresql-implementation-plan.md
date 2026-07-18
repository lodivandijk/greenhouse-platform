# PostgreSQL Implementation Plan

## Objective

Introduce PostgreSQL as the persistent storage layer for the Greenhouse
Platform.

## Phase 1

-   Install PostgreSQL on Raspberry Pi.
-   Create `greenhouse` database.
-   Create `greenhouse_app` role.
-   Configure Spring Boot datasource.
-   Add PostgreSQL JDBC driver.
-   Add Flyway.

## Phase 2

Create initial migration:

-   observation table
-   indexes
-   constraints

## Phase 3

Implement Observation module:

-   Controller
-   Service
-   Repository
-   Entity
-   DTOs

## Phase 4

Implement APIs

-   POST /api/v1/observations
-   GET /api/v1/observations
-   GET /api/v1/observations/latest

## Phase 5

Testing

-   Flyway migration tests
-   Repository tests
-   API integration tests

## Future

-   Digital Twin persistence
-   Decision Engine persistence
-   TimescaleDB evaluation
-   Backup automation
-   Monitoring
