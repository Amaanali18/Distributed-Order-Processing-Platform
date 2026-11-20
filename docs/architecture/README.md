# Architecture

Target architecture for the Distributed Order & Payment Processing Platform.

## Style

Microservices behind a single API gateway, with per-service data ownership.
Synchronous calls are used only where an immediate answer is required
(order -> inventory reservation). Everything else is event-driven over Kafka
with an at-least-once guarantee and idempotent consumers.

## Components

| Component | Owns | Key responsibility |
|---|---|---|
| API Gateway | - | single entry point, JWT validation, rate limiting, correlation IDs |
| Eureka Server | - | service registry and discovery |
| Auth Service | users, roles | registration, login, JWT issuing (RS256) |
| Catalog Service | products, stock, reservations | catalogue and atomic stock reservation |
| Order Service | orders, order items | order lifecycle and saga coordination |
| Payment Service | payment attempts, transactions, refunds | provider integration, idempotency, reconciliation |
| Notification Service | notifications | consume events, notify (dev log / email) |

## Data ownership

Each service owns its tables and exposes them only through its own API. No
service reads or writes another service's schema. Locally, one PostgreSQL
instance hosts one logical database per service (see ADR 0002).

| Service | Database |
|---|---|
| Auth | `dpp_auth` |
| Catalog | `dpp_catalog` |
| Order | `dpp_orders` |
| Payment | `dpp_payments` |
| Notification | `dpp_notifications` |

## Critical workflow

```
client -> gateway -> order: POST /orders
  order -> catalog (Feign): reserve stock
  order persists PENDING + OrderCreated
client -> payment: POST /payments (Idempotency-Key)
  payment creates attempt, Stripe PaymentIntent (provider idempotency key)
client completes checkout (Stripe.js, test mode)
  Stripe webhook -> payment (verify signature, dedupe)
     success -> PaymentSucceeded -> order CONFIRMED -> catalog commit
     failure -> PaymentFailed -> order CANCELLED -> catalog release
late success on a cancelled order -> refund compensation -> REFUNDED
uncertain outcome -> scheduled provider reconciliation (never a blind retry)
```

## Events

Versioned envelope (`platform-common`):

```
eventId, eventType, schemaVersion, aggregateId, occurredAt,
correlationId, producer, payload
```

Topics: `orders.v1`, `inventory.v1`, `payments.v1`, `notifications.v1`, each
with `.retry` and `.DLQ` companions. Messages are keyed by aggregate id.

## Failure handling

Connection and read timeouts, bounded retries with exponential backoff,
Resilience4j circuit breakers around remote calls, Kafka retry topics that
route to a dead-letter topic, and a reconciliation job for uncertain payments.

## Observability

Actuator, Micrometer, Prometheus and Grafana: throughput, latency, error rate,
health, JVM and connection-pool metrics, Kafka consumer lag, dead-letter
counts, and order/payment state distributions. Correlation IDs span gateway,
service calls and event processing.
