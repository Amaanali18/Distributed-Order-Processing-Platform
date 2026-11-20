# ADR 0003: Transactional outbox with at-least-once delivery

- Status: Accepted
- Date: 2026-10-15

## Context

State changes must be recorded in the owning database and announced on Kafka.
Writing to the database and publishing to Kafka are two systems, so a crash
between them can lose or duplicate an event.

## Decision

Use the transactional outbox pattern. In the same local transaction that
changes business state, write an outbox row. A poller publishes unsent rows to
Kafka and marks them sent. Kafka delivery is at-least-once, so every consumer
is idempotent: it records processed `eventId` values in a table with a unique
constraint, inside the same transaction that applies the change.

## Consequences

- No lost events, and duplicates are harmless.
- Consumers must be written to tolerate redelivery; this is enforced by the
  unique constraint rather than by convention.
- Publishing lags the commit by the poll interval (acceptable here).
- Change Data Capture (for example Debezium) is a possible future replacement
  for the poller and is out of scope now.
