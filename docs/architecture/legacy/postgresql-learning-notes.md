# PostgreSQL Learning Notes

## What is PostgreSQL?

PostgreSQL is a database server, not a library.

    Spring Boot
        │
     JDBC
        │
    PostgreSQL Server
        │
    Database Files

The server manages:

-   SQL parsing
-   Query planning
-   Transactions
-   Storage
-   Recovery
-   Security

## Core Concepts

### Cluster

A PostgreSQL installation containing one or more databases.

### Database

A logical container for schemas, tables and data.

### Schema

A namespace containing tables and other objects.

### Table

Stores rows and columns.

### Index

Accelerates data retrieval.

### Role

Database user and permission model.

## Physical Storage

Data is stored internally as binary pages (typically 8 KB).

Applications never manipulate these files directly.

## Query Lifecycle

1.  Client sends SQL.
2.  Parser validates syntax.
3.  Planner chooses execution plan.
4.  Executor retrieves or updates data.
5.  Transaction commits.
6.  Changes are persisted safely.

## Topics to Learn

-   SQL
-   MVCC
-   WAL
-   Buffer cache
-   Indexes
-   VACUUM
-   Query planner
-   EXPLAIN ANALYZE
-   Connection pooling
-   Backup and restore
