# Testing

Inventory of the test assets currently in the repository and how to run them.
This document describes what exists. The validation snapshot below records the
commands that were actually executed; load-test results are not claimed anywhere
in this repo.

## Validation snapshot (2026-11-19)

| Command | Result |
|---|---|
| `.\mvnw.cmd -pl payment-service -am "-Dtest=PaymentApiSecurityIntegrationTest,StripeWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | **Passed** — 8 tests, 0 failures/errors after the `WebhookEventStore` JDBC timestamp fix. |
| `.\mvnw.cmd -q clean test` | **Passed** — Surefire XML reports show 113 tests across 22 test classes, 0 failures/errors, including the concurrent-reservation check and `MetricsRegistryIntegrationTest`. The quiet Maven run emitted no reactor summary; the result was checked from the reports. |
| `.\mvnw.cmd -pl catalog-service -am "-Dtest=InventoryServiceIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | **Passed** — 9 tests, 0 failures/errors, including the new concurrent-reservation oversell check. |
| `docker compose -f infra/docker-compose.full.yml config --quiet` | **Passed** — no output. |
| `docker compose -f infra/docker-compose.full.yml up -d --build` | **Passed** — all images built; all running containers reached `healthy`. |
| `powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p2.ps1` | **Passed** — 16 assertions passed, 0 failed. |
| `powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p3.ps1` | **Passed** — 16 assertions passed, 0 failed. |
| `Invoke-WebRequest http://localhost:8080/v3/api-docs/{auth,catalog,order,payment,notification}` | **Passed** — all five routes HTTP 200 with an `openapi` document. |
| `Invoke-WebRequest http://localhost:{8080..8085,8761}/actuator/prometheus` | **Passed** — all HTTP 200 with `jvm_memory_used_bytes` and `process_cpu_usage`; order/payment/notification also expose `kafka_consumer_fetch_manager_records_lag_max`. |
| `postman spec lint postman/specs/dpp-api.yaml --issueType=syntax --no-report-events` | **Passed** — `No issues found.` |
| `postman collection lint "postman/collections/DPP API"` | **Passed** — 38 entities, 0 errors/warnings. |
| `postman environment lint postman/environments` | **Passed** — 1 environment, 0 errors/warnings. |
| `postman collection run "postman/collections/DPP API" -e "postman/environments/local.environment.yaml" --env-var "admin_password=AdminPass123!" --env-var "internal_token=dpp-internal-dev-token" --env-var "idempotency_key=<unique-key>" --no-report-events --timeout-request 30000` | **Passed** — live simulated stack: 1 iteration, 38 requests, 2 pre-request scripts, 9 test scripts, 9 assertions, 0 failed; exit code 0. |

During the first run after recreating only payment-service, the gateway logged
`No servers available for service: payment-service` and P2 returned 503 because
Spring Cloud LoadBalancer had cached an empty instance list. The gateway now uses
`spring.cloud.loadbalancer.cache.ttl: 5s`; after rebuilding and recreating the
gateway container, both smoke suites passed 16/16. See the runbook for the
troubleshooting signature.

`postman spec lint ... --no-report-events` (governance mode) was not completed
because it requires a Postman login. The CLI printed the same guest notice
during the collection run but executed the local collection successfully with
`--no-report-events`; no Postman workspace state was changed. Postman MCP tools
were not exposed in this agent's active toolset, so the verified run used the
local CLI. A few unasserted sample requests intentionally return non-2xx without
seeded data (inventory examples, cleanup cancel after order confirmation); they
are documented in `postman/README.md`. No k6 results are recorded.

## Test layers and actual test files

### Unit tests (plain JUnit, no Spring container)

| File | What it proves |
|---|---|
| `platform-common/src/test/java/com/dpp/common/error/ApiErrorTest.java` | Shared error envelope defaults, custom messages/violations and `ApiException` carrying an `ErrorCode`. |
| `platform-common/src/test/java/com/dpp/common/event/EventEnvelopeTest.java` | Event identity/metadata, distinct event ids, and `.retry` / `.DLQ` topic suffix helpers. |
| `platform-common/src/test/java/com/dpp/common/web/CorrelationIdFilterTest.java` | Correlation id generation and reuse of an incoming `X-Correlation-Id`, plus MDC cleanup. |
| `payment-service/src/test/java/com/dpp/payment/provider/ResilientPaymentProviderTest.java` | Resilience4j retry and circuit-breaker behavior around the `PaymentProvider` port using a stub delegate. |
| `payment-service/src/test/java/com/dpp/payment/web/StripeWebhookControllerTest.java` | Webhook claim lifecycle: duplicate acknowledgement, 409 on in-progress, `PROCESSED` after success and `FAILED` after handling errors. |
| `auth-service/src/test/java/com/dpp/auth/config/JwtConfigurationTest.java` | Persistent RSA JWK generation/reuse across restarts and fail-closed behavior when generation is disabled. |

Support class (not a test): `platform-testing/src/main/java/com/dpp/testing/AbstractPostgresIntegrationTest.java`
registers a Testcontainers PostgreSQL datasource for the integration tests.

### Integration tests (Spring context; most use Testcontainers PostgreSQL)

| File | What it proves |
|---|---|
| `api-gateway/src/test/java/com/dpp/gateway/GatewayIntegrationTest.java` | Gateway security filters: requests without a bearer token are rejected (401) and an authenticated user without the required role is forbidden (403), using a test RSA keypair and a stub upstream route. |
| `auth-service/src/test/java/com/dpp/auth/AuthServiceIntegrationTest.java` | Registration persistence, duplicate email handling, login token issuance/decoding, wrong/unknown credentials, refresh rotation and `/auth/me`. |
| `auth-service/src/test/java/com/dpp/auth/AuthApiIntegrationTest.java` | HTTP contract for `/auth/register`, `/auth/login` and `/auth/me`, including validation and conflict/unauthorized responses. |
| `auth-service/src/test/java/com/dpp/auth/AdminBootstrapIntegrationTest.java` | Bootstrap admin is seeded with `ADMIN` and `CUSTOMER` roles. |
| `catalog-service/src/test/java/com/dpp/catalog/ProductServiceIntegrationTest.java` | Product creation, duplicate SKU rejection, active-only listing, updates and restock. |
| `catalog-service/src/test/java/com/dpp/catalog/InventoryServiceIntegrationTest.java` | Atomic reserve with idempotency, insufficient-stock and inactive-product rejection, commit, release, expiry sweep, rejection of commit after release and a concurrent-reservation check that stock cannot be oversold. |
| `catalog-service/src/test/java/com/dpp/catalog/CatalogApiSecurityIntegrationTest.java` | Public product reads, anonymous/customer denied on admin product creation, admin allowed, 404 for unknown ids. |
| `order-service/src/test/java/com/dpp/order/OrderServiceIntegrationTest.java` | Server-side totals, ownership checks, cancellation, state transitions from payment events, duplicate event handling, refund transitions. |
| `order-service/src/test/java/com/dpp/order/OrderApiSecurityIntegrationTest.java` | Anonymous/customer/admin access rules for `/orders` and `/admin/orders`, including cross-customer denial. |
| `order-service/src/test/java/com/dpp/order/OutboxRelayIntegrationTest.java` | Exhausted outbox rows are routed to `<topic>.DLQ`; rows stay PENDING with backoff when the dead-letter publish also fails. |
| `order-service/src/test/java/com/dpp/order/MetricsRegistryIntegrationTest.java` | Regression guard for Kafka listener error-handler initialization: the Micrometer registry still binds JVM and process meters, so `/actuator/prometheus` stays complete. |
| `payment-service/src/test/java/com/dpp/payment/PaymentServiceIntegrationTest.java` | Idempotency key uniqueness and payload-mismatch rejection, confirm success/failure, refunds, provider events, late-success compensation, audit transitions and ownership. |
| `payment-service/src/test/java/com/dpp/payment/PaymentRefundPendingIntegrationTest.java` | Pending refunds are recorded then completed on reconciliation; failed refunds revert the attempt to `SUCCESS`. |
| `payment-service/src/test/java/com/dpp/payment/PaymentApiSecurityIntegrationTest.java` | Anonymous denial, missing idempotency key, customer creation and the public webhook path with the simulated adapter. |
| `notification-service/src/test/java/com/dpp/notification/NotificationServiceIntegrationTest.java` | Rendering and persistence for order/payment events, recipient read-model resolution, duplicate-event dedupe, unhandled events and failed delivery recording. |
| `notification-service/src/test/java/com/dpp/notification/NotificationApiSecurityIntegrationTest.java` | Ownership and role rules for `/notifications` and `/admin/notifications`. |

All payment/order integration tests use the **simulated** provider; no automated
test depends on a live payment provider.

### Smoke tests (running stack, PowerShell)

| File | What it proves |
|---|---|
| `infra/scripts/smoke-p2.ps1` | End-to-end P2 flow: anonymous denial, admin/customer auth, product creation, order + stock reservation, idempotent payment creation, `SUCCEEDED` confirm, saga confirmation, inventory commit, cancellation and stock release. |
| `infra/scripts/smoke-p3.ps1` | P3 notifications: auth rules, the same order/payment flow, and delivery of `OrderCreated`, `PaymentSucceeded` and `OrderConfirmed` notifications to the customer, plus admin listing and customer role denial. |

### Postman collection (running stack, local CLI)

`postman/collections/DPP API/` is a Git-native v3 collection run against the
gateway and the direct service URLs recorded in
`postman/environments/local.environment.yaml`. Its nine assertions cover login
token shapes, admin token storage, active-product listing, order creation,
payment creation, customer/admin notification listing, the no-op Stripe webhook
and gateway health. The final live run reported 38/38 requests executed and
9/9 assertions passed with the simulated provider. See `postman/README.md` for
the exact command, variable overrides and notes on unasserted sample requests.

### Load tests (k6, simulated provider via gateway)

| File | What it proves |
|---|---|
| `load-tests/k6/products.js` | p95/error-rate gate for `GET /products?page&size` under configurable VU load. |
| `load-tests/k6/orders.js` | p95/error-rate gate for authenticated `POST /orders` and the synchronous stock reservation. |
| `load-tests/k6/payments.js` | p95/error-rate gate for `POST /payments` with unique idempotency keys through the simulated adapter. |

Scripts, environment variables, result capture and threshold semantics are in
`load-tests/k6/README.md`. Reporting template: `load-tests/k6/results/TEMPLATE.md`.
No load tests were run for this documentation refresh; no results are recorded.

## Commands

Run from the repository root. Maven is optional locally; use `./mvnw` on POSIX
and `.\mvnw.cmd` on Windows PowerShell.

### Build and all unit/integration tests

```powershell
.\mvnw.cmd test
```

```sh
./mvnw test
```

Add `-DskipTests package` when you only want runnable jars for smoke/load tests:

```powershell
.\mvnw.cmd -q -DskipTests package
```

### One module or one test class

```powershell
.\mvnw.cmd -pl payment-service -am test
.\mvnw.cmd -pl payment-service -Dtest=PaymentServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```sh
./mvnw -pl payment-service -am test
./mvnw -pl payment-service -Dtest=PaymentServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Integration tests use Testcontainers, so the Docker daemon must be running and
available to the user executing Maven.

### Smoke tests against a running local stack

Start dependencies, build jars, start the stack (see `docs/runbook.md`), then:

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p2.ps1
powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p3.ps1
```

Params: `-Gateway`, `-AdminEmail`, `-AdminPassword`. Each script prints
`PASS`/`FAIL` lines and exits non-zero if any assertion fails.

### k6 load tests

```powershell
cd load-tests\k6
k6 run products.js
k6 run orders.js
k6 run payments.js
```

```sh
cd load-tests/k6
k6 run products.js
k6 run orders.js
k6 run payments.js
```

Set `VUS`, `DURATION`, `P95_MS`, `MAX_ERROR_RATE` and other variables as shown in
`load-tests/k6/README.md`. Do not point the k6 scripts at a stack configured with
`PAYMENT_PROVIDER=stripe`.

## What each layer proves

- **Unit tests** isolate pure logic: error/event shapes, correlation handling and
  resilience-policy wiring. They do not prove persistence or HTTP behavior.
- **Integration tests** prove each service's own database constraints, state
  machines, authorization rules and event publication against a real PostgreSQL
  instance. The gateway test covers its JWT/role filters against a stub upstream;
  end-to-end cross-service routing is exercised by the smoke tests.
- **Smoke tests** prove the assembled system: gateway routing, JWT validation,
  all service interactions, Kafka saga progression, inventory effects and
  notification delivery with the simulated provider.
- **k6 scripts** measure throughput, latency and error rates for three defined
  workloads. They prove performance gates under a specific configuration; they
  do not prove correctness beyond the response-shape checks they contain.

## Known gaps that still need manual verification

- Real Stripe test-mode integration (PaymentIntent creation, signature
  verification, duplicate webhook delivery, reconciliation): see
  `docs/stripe-manual-verification.md`. No automated test covers the Stripe
  adapter against Stripe.
- Webhook events are claimed in `processed_webhook_events` with status
  `PROCESSING` and marked `PROCESSED` only after successful domain handling;
  failed handling is marked `FAILED` for redelivery. The manual duplicate-delivery
  check is in `docs/stripe-manual-verification.md`.
- Gateway rate limiting is configured in
  `api-gateway/src/main/resources/application.yml` as a Redis-backed
  `RequestRateLimiter` (`GATEWAY_RATE_LIMIT_REPLENISH_RATE`,
  `GATEWAY_RATE_LIMIT_BURST_CAPACITY`, `REDIS_HOST`, `REDIS_PORT`). It has not
  been exercised against a running stack here; verify the deployed configuration
  and behavior before claiming it works.
- Prometheus/Grafana are intentionally external; no containers ship for them.
  Scrape targets are documented in `docs/observability.md`; dashboards need
  manual setup and verification.
- Kafka broker outage and consumer-group recovery with real broker restarts,
  including safe DLQ replay, are manual runbook procedures (`docs/runbook.md`).
- Late payment success after cancellation is covered at the service level by
  integration tests; the full browser/UI flow and mixed-service timing are not.
- Long-duration load and token expiry beyond the 15-minute access-token TTL are
  not covered by the default scripts.
- No frontend end-to-end UI test suite is present in this repository.
