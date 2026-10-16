# Distributed Order & Payment Processing Platform

A full-stack, event-driven microservices platform for order and payment
processing: React frontend, Spring Boot / Spring Cloud backend, PostgreSQL,
Kafka, Redis, and a payment provider in test mode.

This is a portfolio and educational project. It is not a production financial
platform.

## Status

Phase **P0 — Foundations** (in progress): repository layout, parent build,
shared platform library, local infrastructure dependencies, and API contract
conventions. See `docs/architecture/` and `docs/adr/` for decisions.

## Repository layout

```
.
├─ pom.xml                 Maven aggregator / parent
├─ platform-common/        shared event envelope, error model, correlation
├─ platform-testing/       shared Testcontainers support
├─ infra/                  docker-compose and dependency configuration
├─ postman/                local Postman contract artifacts and skills
└─ docs/                   architecture notes and ADRs
```

Service modules (discovery server, gateway, auth, catalog, order, payment,
notification) and `frontend/` are added in later phases.

## Prerequisites

- JDK 21 (the build targets Java 21; `maven.compiler.release=21`)
- Docker with Docker Compose v2
- Node.js 20+ (frontend, phase P3)

Maven is not required locally; a wrapper is provided (`mvnw` / `mvnw.cmd`).

## Build

```
./mvnw -q -DskipTests package      # PowerShell: .\mvnw.cmd -q -DskipTests package
```

## Local dependencies

Start PostgreSQL, Redis, and Kafka:

```
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

PostgreSQL is initialised with one logical database per service
(`dpp_auth`, `dpp_catalog`, `dpp_orders`, `dpp_payments`, `dpp_notifications`).
Kafka is reachable at `localhost:9092` from the host and `kafka:29092` from
inside the compose network.

Stop (keep data) or reset (delete data):

```
docker compose -f infra/docker-compose.yml down
docker compose -f infra/docker-compose.yml down -v
```

## Documentation

- `docs/architecture/README.md` — target architecture and service ownership
- `docs/architecture/conventions.md` — naming, packaging, migrations, events
- `docs/adr/` — architecture decision records
