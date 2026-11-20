# ADR 0002: Database per service

- Status: Accepted
- Date: 2026-10-15

## Context

Services must own their data. Options: separate PostgreSQL instances per
service, one instance with one logical database per service, or one database
with one schema per service.

## Decision

One PostgreSQL instance, one logical database per service (`dpp_auth`,
`dpp_catalog`, `dpp_orders`, `dpp_payments`, `dpp_notifications`). Each service
connects only to its own database and migrates it with Flyway.

## Consequences

- The application boundary matches the data boundary: no cross-service joins or
  foreign keys, and no direct writes to another service's tables.
- Local development stays lightweight (one container) while still isolating
  data at the database level.
- Schema-per-service was rejected as weaker isolation; a container per service
  was rejected as unnecessarily heavy locally.
- Because there is no shared transaction, cross-service workflows use explicit
  compensation and at-least-once messaging (see ADR 0003).
