# Distributed Order & Payment Processing Platform

A full-stack, event-driven microservices platform for order and payment
processing: React frontend, Spring Boot / Spring Cloud backend, PostgreSQL,
Kafka, Redis, and a payment provider in test mode.

This is a portfolio and educational project. It is not a production financial
platform.

## Status

**P0** (repository layout, parent build, shared platform libraries, local
infrastructure), **P1** (service discovery, API gateway, auth/JWT, catalog +
inventory), **P2** (orders & payments with the Kafka saga, transactional outbox,
idempotent consumers, simulated/Stripe payment provider) and **P3** (notification
service consuming order/payment events and delivering customer notices via log
or SMTP) are complete. A React + Vite frontend now lives in `frontend/`.
The full Maven test suite passes (113 tests across 22 test classes, 0
failures/errors, 2026-11-18, including the concurrent-reservation check and the
Micrometer registry regression test).
The complete Docker Compose stack in `infra/docker-compose.full.yml` was built
and started successfully: all containers reached `healthy`, both PowerShell
smoke suites passed 16/16, and all five gateway OpenAPI JSON routes returned
HTTP 200 with an `openapi` document. See `docs/architecture/` and `docs/adr/`
for decisions, `docs/testing.md` for the test inventory, `docs/observability.md` for metrics
exposure and `docs/runbook.md` for operations.

## Repository layout

```
.
├─ pom.xml                 Maven aggregator / parent
├─ platform-common/        shared event envelope, error model, correlation
├─ platform-messaging/     transactional outbox, idempotency, DLQ support
├─ platform-testing/       shared Testcontainers support
├─ discovery-server/       Eureka service registry        (8761)
├─ api-gateway/            Spring Cloud Gateway           (8080)
├─ auth-service/           users, JWT issuance            (8081)
├─ catalog-service/        products + inventory           (8082)
├─ order-service/          orders + saga orchestration    (8083)
├─ payment-service/        payments, refunds, webhooks    (8084)
├─ notification-service/   event-driven customer notices  (8085)
├─ frontend/               React + Vite SPA             (5173 dev / 3000 image)
├─ infra/                  docker-compose and dependency configuration
├─ postman/                local Postman contract artifacts and skills
└─ docs/                   architecture notes and ADRs
```

## Prerequisites

- JDK 21 (the build targets Java 21; `maven.compiler.release=21`)
- Docker with Docker Compose v2
- Node.js 20.19+ or 22.12+ (frontend build/dev; Vite 8)

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

## Full stack (Docker Compose)

`infra/docker-compose.full.yml` brings up PostgreSQL, Redis, Kafka, all seven
Spring Boot applications and the frontend (nginx on port 3000) with one command:

```powershell
docker compose -f infra/docker-compose.full.yml up -d --build
docker compose -f infra/docker-compose.full.yml ps
docker compose -f infra/docker-compose.full.yml down          # keep data
docker compose -f infra/docker-compose.full.yml down -v       # reset data
```

The Compose file passed `docker compose config --quiet` and `docker build --check`.
`up -d --build` then built every image and all running containers reached
`healthy`; the P2 and P3 smoke scripts each reported 16 passed, 0 failed. The
local Postman collection also ran against the simulated stack: 38 requests and
9/9 assertions passed with exit code 0; nothing was pushed or published to a
Postman workspace. See `docs/runbook.md` §1.4 for the exact validation record
and troubleshooting notes, and `docs/testing.md` for the Postman run evidence.

## Frontend

The React + Vite app in `frontend/` talks to the gateway at
`http://localhost:8080` (dev server on `http://localhost:5173`):

```
cd frontend
npm install
npm run dev
```

See `frontend/README.md` for configuration, roles and build commands.

## Documentation

- `docs/architecture/README.md` — target architecture and service ownership
- `docs/architecture/conventions.md` — naming, packaging, migrations, events
- `docs/adr/` — architecture decision records
- `docs/runbook.md` — startup, shutdown, reset and troubleshooting
- `docs/observability.md` — actuator endpoints, custom gauges, scrape targets and correlation ids
- `docs/testing.md` — unit, integration, smoke and k6 load-test inventory with recorded validation results; no load results are claimed
- `load-tests/k6/README.md` — k6 scripts, environment variables and thresholds
- `postman/` (collections, environments, flows, specs) and `.postman/` — local Postman contract artifacts
- Springdoc JSON via the gateway at
  `http://localhost:8080/v3/api-docs/{auth,catalog,order,payment,notification}`;
  Swagger UI remains available directly on each service port.
- `docs/stripe-manual-verification.md` — opt-in Stripe test-mode procedure; no automated test uses Stripe
