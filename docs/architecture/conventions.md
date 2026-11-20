# Conventions

## Modules

- Maven multi-module build under a single aggregator POM (`com.dpp:dpp-parent`).
- Group id `com.dpp`; each service is its own module with an executable
  `spring-boot-maven-plugin` build.
- Shared code lives in `platform-common` (runtime), `platform-messaging`
  (transactional outbox, consumer idempotency, dead-letter handling), and
  `platform-testing` (test support). Domain logic does not live in shared
  modules.

## Package layout

Base package `com.dpp`. Within a service:

```
com.dpp.<service>
├─ config        Spring configuration and beans
├─ domain        entities, value objects, state machines
├─ repository    Spring Data repositories
├─ service       application services / use cases
├─ web           controllers and DTOs
├─ client        outbound Feign clients
├─ messaging     Kafka producers/consumers
└─ exception     service-specific exceptions and handlers
```

## Naming

- Controllers end in `Controller`, application services in `Service`,
  DTOs in `Request`/`Response`, entities without suffix.
- REST paths are plural nouns (`/products`, `/orders`), admin actions under
  `/admin/...`, service-to-service calls under `/internal/...`.

## Error contract

Every service returns the shared `ApiError` shape (see `platform-common`).
Business failures raise `ApiException` with an `ErrorCode`; the auto-configured
`GlobalExceptionHandler` renders the response and attaches the correlation id.

## Correlation

Clients send `X-Correlation-Id`; the gateway and services propagate it. It is
placed in the SLF4J MDC under `correlationId` and echoed on responses.

## Database migrations

- Flyway, one migration folder per service:
  `src/main/resources/db/migration`.
- File names: `V<major>_<minor>__<description>.sql` (for example
  `V1_0__create_orders.sql`). Never edit an applied migration; add a new one.
- Each service migrates only its own database.

## Events

- Use `EventEnvelope` from `platform-common`; never publish a bare payload.
- `eventType` values come from `EventTypes`; Kafka topics from `Topics`.
- Consumers deduplicate on `eventId` within the same transaction that applies
  the state change.
- Important state changes publish through the transactional outbox.

## Transactional outbox and idempotency

- Publishers write events with `OutboxWriter` in the same transaction as the
  state change; `OutboxRelay` publishes them to Kafka with retries and skips
  rows locked by other instances (`FOR UPDATE SKIP LOCKED`).
- When a row exhausts `dpp.outbox.max-attempts`, the relay republishes the
  original envelope to `<topic>.DLQ` and marks the row `DEAD_LETTERED`. If that
  dead-letter publish also fails the row stays `PENDING` with backoff and a
  `last_error` note, so no event is ever dropped.
- Consumers guard against duplicates with `ProcessedEventStore.firstTime`,
  inserting into `processed_events` (unique `event_id`) in the same transaction.
- Every service that publishes or consumes owns its own `outbox_messages` and
  `processed_events` tables (see each service's Flyway migration).
- Bind JDBC timestamps as `OffsetDateTime` (UTC), not `Instant`; pgjdbc cannot
  infer the SQL type for `Instant` directly.

## Service-to-service calls

- Synchronous calls use Feign clients targeting a registered service name
  (for example `catalog-service`), resolved through Eureka + load balancing.
- Protected internal endpoints live under `/internal/...` and require the
  shared header `X-Internal-Service-Token` (`dpp.internal.service-token`).
- `/internal/**` is never routed through the public gateway.
- Outbound calls are wrapped in Resilience4j (circuit breaker + retry) with
  bounded connect/read timeouts. `order-service` decorates the `catalog-service`
  Feign client; `payment-service` wraps the `PaymentProvider` port. Tune with
  `dpp.*.resilience.*` and the Feign timeout properties.

## Payment provider

- Payment code depends on the `PaymentProvider` port, not on a vendor SDK.
- Two adapters: `simulated` (default, in-memory) and `stripe` (REST + HMAC
  webhook verification). Select with `dpp.payment.provider`.
- Payments are created with a required `Idempotency-Key` header; reuse with a
  different payload is rejected with `IDEMPOTENCY_KEY_REUSE`.

## Notifications

- `notification-service` consumes `orders.v1` and `payments.v1` and deduplicates
  with `ProcessedEventStore` like any other consumer.
- Delivery goes through the `NotificationSender` port (`channel()` + `send`).
  Two adapters: `log` (default, writes to the service log) and `smtp`
  (Spring `JavaMailSender`). Select with `dpp.notification.sender`.
- A sender failure records the notification as `FAILED` with its error rather
  than failing the whole event, so it is not redelivered forever.
- Order events carry the customer email in the payload; payment events have no
  email, so the service resolves the recipient from its local
  `notification_recipients` read model, seeded on `OrderCreated`.

## Secrets

- No secrets in source control. Supply values through environment variables
  or `.env` files (git-ignored). `.env.example` documents required keys.
- Never log tokens, authorization headers, or raw payment data.
