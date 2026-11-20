# Observability

Metrics-only observability for the Distributed Order & Payment Processing
Platform. This document describes what is configured in the repository; it
does not claim that any endpoint was scraped or that any dashboard was verified.

## Actuator exposure

All seven Spring Boot applications — `api-gateway`, `discovery-server`,
`auth-service`, `catalog-service`, `order-service`, `payment-service` and
`notification-service` — expose `health,info,metrics,prometheus` under
`/actuator`.

Additional endpoints:

| Application | Extra exposed endpoints |
|---|---|
| `api-gateway` | `gateway` (route information) |
| `order-service` | `circuitbreakers`, `retries` (catalog call policies) |

Liveness/readiness probes are enabled under `/actuator/health` (`probes.enabled:
true` in each `application.yml`).

## Metrics implementation

- Each of the seven applications declares
  `io.micrometer:micrometer-registry-prometheus` with `runtime` scope, so
  `/actuator/prometheus` serves a Prometheus-format scrape of the Micrometer
  registry.
- Standard Spring Boot Actuator/Micrometer JVM, process, HTTP, HikariCP and
  Kafka consumer metrics (including
  `kafka_consumer_fetch_manager_records_lag_max` on the Kafka-consuming services)
  are available from the same endpoint.
- Two custom status gauges are registered at startup:

  | Metric | Tags | Source |
  |---|---|---|
  | `dpp.orders.status` | `status=<OrderStatus>` | `order-service/.../metrics/OrderStateMetrics.java`, backed by `OrderRepository.countByStatus` |
  | `dpp.payments.status` | `status=<PaymentStatus>` | `payment-service/.../metrics/PaymentStateMetrics.java`, backed by `PaymentAttemptRepository.countByStatus` |

  Both are gauges whose value is the current row count for that status.

## Correlation ids

- The gateway's `CorrelationIdGlobalFilter` reads `X-Correlation-Id` from an
  incoming request, generates a UUID when it is absent or blank, and writes the
  value to the response header.
- `CorrelationIdFilter` in `platform-common` applies the same header rule to
  service requests, puts the value in the MDC under `correlationId`, and clears
  it afterwards. The shared error envelope also carries the correlation id.
- Event envelopes carry `correlationId`, so it can be followed from a gateway
  request through HTTP calls and Kafka consumers.

Where to search:

- Local jar stack: `logs/<service>.log` and `logs/<service>.err.log` for
  `discovery-server`, `auth-service`, `catalog-service`, `order-service`,
  `payment-service`, `notification-service` and `api-gateway`. Example on
  Windows PowerShell:

  ```powershell
  Select-String -Path logs\*.log -Pattern '<correlation-id>'
  ```

- Full Compose stack: `docker compose -f infra/docker-compose.full.yml logs
  <service>` (or `logs -f <service>`), grepping for the same id. Infrastructure
  logs are available from the same command with `postgres`, `redis` or `kafka`.

Never log tokens, authorization headers, secret keys or raw payment data.

## Scrape targets for local development

All targets are plain HTTP on the host and use the path `/actuator/prometheus`.
Prometheus itself is not included, so these are the URLs to configure in an
external scraper.

| Application | Host port | Scrape URL |
|---|---|---|
| API Gateway | 8080 | `http://localhost:8080/actuator/prometheus` |
| Auth Service | 8081 | `http://localhost:8081/actuator/prometheus` |
| Catalog Service | 8082 | `http://localhost:8082/actuator/prometheus` |
| Order Service | 8083 | `http://localhost:8083/actuator/prometheus` |
| Payment Service | 8084 | `http://localhost:8084/actuator/prometheus` |
| Notification Service | 8085 | `http://localhost:8085/actuator/prometheus` |
| Discovery Server (Eureka) | 8761 | `http://localhost:8761/actuator/prometheus` |

## Not provided

- No Prometheus or Grafana containers ship in this repository (PRD §16 keeps
  the local runtime lightweight); Prometheus/Grafana must be supplied externally.
- No dashboards, alert rules or long-term metric storage are included.
- Gateway and all application security chains now permit `/actuator/prometheus`
  (only that actuator endpoint, not business endpoints). It was live-verified on
  ports 8080–8085 and 8761: all returned HTTP 200 with
  `jvm_memory_used_bytes` and `process_cpu_usage` in the body. Keep these ports
  on a trusted network. The Kafka consumers additionally report
  `kafka_consumer_fetch_manager_records_lag_max`.
- The full Compose stack was built and started; all running containers reached
  `healthy` (see `docs/runbook.md` §1.4).
- No scrape retention, dashboard or alert is configured; Prometheus/Grafana are
  still external and no long-term storage ships with the repo.
- No load-test results are claimed. k6 scripts and a reporting template are in
  `load-tests/k6/`; see `docs/testing.md`.
