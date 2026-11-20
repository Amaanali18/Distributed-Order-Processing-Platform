# Runbook

Local operations for the Distributed Order & Payment Processing Platform.
Commands are given for Windows PowerShell and POSIX shells where they differ.
Run from the repository root.

Ports in use: gateway `8080`, auth `8081`, catalog `8082`, order `8083`,
payment `8084`, notification `8085`, Eureka `8761`; infrastructure PostgreSQL
`5433` (host), Redis `6379`, Kafka `9092` (host) / `29092` (compose network).

## 1. Startup

### 1.1 Dependencies (Docker Compose)

```powershell
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

```sh
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

This starts PostgreSQL (one logical database per service), Redis and Kafka.
Wait for all three to be healthy before starting the services.

### 1.2 Build the services

JDK 21 is required. The Maven wrapper is checked in.

```powershell
.\mvnw.cmd -q -DskipTests package
```

```sh
./mvnw -q -DskipTests package
```

### 1.3 Start the local stack (jars)

`infra/scripts/start-local-stack.ps1` starts Eureka first, then auth/catalog,
then order/payment/notification, then the gateway. It waits for each health
endpoint, sets the local-only admin bootstrap and internal token, and writes
PIDs to `logs/stack-pids.txt`.

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\start-local-stack.ps1
```

POSIX (requires PowerShell 7, `pwsh`):

```sh
pwsh ./infra/scripts/start-local-stack.ps1
```

Optional parameters: `-AdminEmail`, `-AdminPassword`. The defaults are the
documented local-only values from `.env.example` (`admin@dpp.local` /
`AdminPass123!`). The script sets `PAYMENT_PROVIDER=simulated` and
`DPP_NOTIFICATION_SENDER=log`.

Quick verification:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod 'http://localhost:8080/products?page=0&size=1'
```

### 1.4 Full Docker Compose (verified)

`infra/docker-compose.full.yml` starts the infrastructure (PostgreSQL, Redis,
Kafka), all seven Spring Boot applications and the React frontend (nginx on
port 3000) with one command (PRD §12/§16). The file passed
`docker compose -f infra/docker-compose.full.yml config --quiet` and per-service
`docker build --check`; `up -d --build` then built every image and all running
containers reached `healthy` with Docker engine 29.9.3.

A one-shot `auth-jwk-init` container chowns the `dpp-auth-jwk` named volume
before auth-service starts. This repairs volumes created before auth-service ran
as the non-root `dpp` user; fresh volumes already inherit `/keys` ownership from
the image. The notification mail health indicator is disabled by default
because the default sender is log-only; set `MANAGEMENT_HEALTH_MAIL_ENABLED=true`
when SMTP is configured.

Start (build images, create volumes, detach):

```powershell
docker compose -f infra/docker-compose.full.yml up -d --build
docker compose -f infra/docker-compose.full.yml ps
```

Shutdown and reset:

```powershell
docker compose -f infra/docker-compose.full.yml down          # keep data
docker compose -f infra/docker-compose.full.yml down -v       # delete all volumes
```

Validation on the running stack (2026-11-19):

- `docker compose -f infra/docker-compose.full.yml ps` — all running containers `healthy`.
- `powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p2.ps1` — 16 passed, 0 failed.
- `powershell -ExecutionPolicy Bypass -File infra\scripts\smoke-p3.ps1` — 16 passed, 0 failed.
- `/v3/api-docs/{auth,catalog,order,payment,notification}` through the gateway — all HTTP 200 with an `openapi` document.
- `/actuator/prometheus` on ports 8080–8085 and 8761 — all HTTP 200 with `jvm_memory_used_bytes` and `process_cpu_usage`; order/payment/notification also expose `kafka_consumer_fetch_manager_records_lag_max`.
- Gateway load-balancer cache TTL is 5s so an empty instance list cached during a service recreate does not suppress routing for the default 35s.
- `postman collection run "postman/collections/DPP API" -e "postman/environments/local.environment.yaml" --env-var "admin_password=AdminPass123!" --env-var "internal_token=dpp-internal-dev-token" --env-var "idempotency_key=<unique-key>" --no-report-events --timeout-request 30000` — 38 requests, 9/9 assertions passed, exit code 0 against the simulated provider. No workspace push or publish was performed.

The jar-based flow in §1.1–§1.3 remains available for faster backend iteration.
Do not point smoke/load tests at a partial stack.

## 2. Shutdown and reset

### 2.1 Stop services, keep data

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\stop-local-stack.ps1
docker compose -f infra/docker-compose.yml down
```

The stop script kills only PIDs recorded in `logs/stack-pids.txt`. If that file
is missing, the script does nothing; use the port-conflict procedure below to
find orphaned processes.

### 2.2 Reset all local data

**Destructive:** removes the PostgreSQL volume, so all users, orders, payments,
notifications and deduplication history are lost.

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\stop-local-stack.ps1
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
```

Flyway migrations run again on the next service start. Logs in `logs/` are not
deleted by this procedure.

## 3. Health checks

| Check | Command or URL | Expected |
|---|---|---|
| Eureka | `http://localhost:8761/actuator/health` | 200 with status `UP` |
| Gateway | `http://localhost:8080/actuator/health` | 200 with status `UP` |
| Auth | `http://localhost:8081/actuator/health` | 200 with status `UP` |
| Catalog | `http://localhost:8082/actuator/health` | 200 with status `UP` |
| Order | `http://localhost:8083/actuator/health` | 200 with status `UP` |
| Payment | `http://localhost:8084/actuator/health` | 200 with status `UP` |
| Notification | `http://localhost:8085/actuator/health` | 200 with status `UP` |
| Public catalogue via gateway | `http://localhost:8080/products?page=0&size=1` | 200 with a `content` array |
| OpenAPI via gateway | `http://localhost:8080/v3/api-docs/catalog` | 200 with an `openapi` document (same route shape for `auth`, `order`, `payment`, `notification`) |
| Auth JWKS | `http://localhost:8081/.well-known/jwks.json` | 200 with `keys` |
| Containers | `docker compose -f infra/docker-compose.full.yml ps` | all running services healthy |
| Kafka topics | `docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` | includes `orders.v1`, `payments.v1` once services publish |
| PostgreSQL | `docker compose -f infra/docker-compose.yml exec -T postgres pg_isready -U dpp -d dpp` | accepting connections |

The one-shot `auth-jwk-init` service exits 0 after chowning the JWT volume; it is
not expected to appear as a running container in `ps`.

All seven Spring Boot applications (discovery-server, api-gateway, auth,
catalog, order, payment and notification) expose
`health,info,metrics,prometheus` under `/actuator`. The gateway additionally
exposes `gateway`; order-service additionally exposes
`circuitbreakers,retries` (useful when the catalog circuit breaker opens).
Scrape targets and log/correlation-id guidance are in `docs/observability.md`.
Prometheus and Grafana are intentionally external; no containers for them ship
in this repository.

## 4. Common failures

### Kafka not ready

Symptoms: order/payment startup warnings, outbox logs
`Outbox publish failed ...`, orders remain `PENDING` after payment success, no
notifications. Checks:

```powershell
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs --tail=200 kafka
docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Recovery: wait for the Kafka healthcheck, then `docker compose -f infra/docker-compose.yml restart kafka`. Services use
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092` from the host and `kafka:29092` inside
the compose network; a wrong value is a common cause. Outbox rows remain
`PENDING` and publish automatically after the broker is available. Do not
delete them.

### JWKS unreachable

Symptoms: every authenticated request returns 401 with token-validation errors,
or services fail to start their resource server. Checks: auth-service health on
8081, `http://localhost:8081/.well-known/jwks.json`, and `AUTH_JWKS_URI` for
gateway and business services. Recovery: start auth-service first, then restart
the affected service. Access tokens are short-lived (15 minutes), so stale
tokens in an old smoke script can also look like a JWKS problem.

### Cold-start Feign timeouts

Symptoms: `POST /orders` or `POST /payments` returns 500 with
`NoFallbackAvailableException` shortly after a Compose restart, and the calling
service log shows `TimeLimiter ... recorded a timeout exception`. Resilience4j
wraps Feign clients; its TimeLimiter must be at least as long as the Feign read
timeout (both services use 5s). If the symptom persists after services have
warmed up, check Eureka registration and the target service's health rather than
raising the limit blindly.

### Gateway returns 503 after a service recreate

Symptoms: a route through `http://localhost:8080` returns 503 immediately after
recreating one backend container, while the backend itself is healthy, and the
gateway log shows `No servers available for service: <name>`. Spring Cloud
LoadBalancer caches service instances; the default 35s TTL can preserve an empty
list from before the service re-registered. The gateway is configured with
`spring.cloud.loadbalancer.cache.ttl: 5s`. If this still appears, check the
gateway log and Eureka registration, wait a few seconds, then retry; recreating
the gateway container clears the cache immediately.

### JWT volume permission denied

Symptoms: auth-service exits 1 with
`java.nio.file.AccessDeniedException: /keys/auth-jwk.json`. Cause: the
`dpp-auth-jwk` named volume was created before the image supplied a `dpp`-owned
`/keys` directory, so it is root-owned. Recovery: re-run
`docker compose -f infra/docker-compose.full.yml up -d --build`; the one-shot
`auth-jwk-init` service chowns the volume before auth-service starts. With an
older compose file, remove the stale local volume (`docker volume rm
dpp_dpp-auth-jwk`) before restarting. This discards only the local signing key;
users can log in again, but previously issued tokens stop validating.

### Docker engine returns HTTP 500

Symptoms: `docker info` or `docker compose ps` fails with `request returned 500
Internal Server Error ... dockerDesktopLinuxEngine`. Recovery: run `docker
desktop restart`, wait until `docker desktop status` reports `running`, then
repeat the compose command. Local volumes are retained.

### Flyway mismatch

Symptoms: startup fails with Flyway checksum/validation errors or Hibernate
`ddl-auto=validate` schema errors. Checks: the service's own
`*_DB_URL` (host port **5433**), the applied migrations in
`<service>/src/main/resources/db/migration`, and the service log. Rules:

- Never edit an already-applied migration. Add a new `Vx_y__...sql` file.
- Each service migrates only its own database (`dpp_auth`, `dpp_catalog`,
  `dpp_orders`, `dpp_payments`, `dpp_notifications`).
- For local development only, `down -v` is a safe way to get a clean schema.
  Do not hand-edit `flyway_schema_history` in a shared environment.

### Port conflicts

Symptoms: a service exits immediately, or startup reports "address already in
use". Find the owner:

```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess
Get-Process -Id <pid> | Select-Object Id,ProcessName,Path
```

```sh
lsof -i :8080
```

Stop the stale process only after identifying it, or use
`stop-local-stack.ps1` if it is a recorded stack process. Common conflicts are
8080-8085, 8761, 5433, 6379 and 9092.

### Missing jars / wrong provider

If the start script cannot find `target/*-1.0.0-SNAPSHOT.jar`, run the package
command from section 1.2. If payment smoke/load tests fail with Stripe errors,
check that payment-service was started with `PAYMENT_PROVIDER=simulated`; the
Stripe path is a manual procedure documented in
`docs/stripe-manual-verification.md`.

## 5. Safe recovery and replay

### 5.1 Transactional outbox

Each publishing service owns `outbox_messages` in its own database. Rows are
`PENDING`, `SENT` or `DEAD_LETTERED`; the relay polls every 500 ms, uses
`FOR UPDATE SKIP LOCKED`, retries with backoff and finally publishes the original
envelope to `<topic>.DLQ`.

Inspect (example for order-service):

```powershell
docker compose -f infra/docker-compose.yml exec -T postgres psql -U dpp -d dpp_orders -c "select status, count(*) from outbox_messages group by status;"
docker compose -f infra/docker-compose.yml exec -T postgres psql -U dpp -d dpp_orders -c "select id, topic, status, attempts, last_error from outbox_messages where status <> 'SENT' order by created_at limit 20;"
```

- `PENDING` after a broker outage: wait. The relay republishes automatically.
- `DEAD_LETTERED`: fix the root cause first, then replay from the DLQ (below).
- Never delete outbox rows to "make the problem go away" and never change a
  payload in place; that destroys the audit trail and event identity.

### 5.2 Kafka retry and DLQ topics

Topics follow `<business>.v1`, `<business>.v1.retry` and `<business>.v1.DLQ`
for `orders`, `inventory`, `payments` and `notifications`. Inspect a DLQ:

```powershell
docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.v1.DLQ --from-beginning --property print.key=true --property key.separator='|'
```

Replay only to the **original business topic**, preserving the original message
key (aggregate id) so idempotent consumers can recognize the event:

```powershell
docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.v1 --property parse.key=true --property key.separator='|'
# Paste lines in the form: <original key>|<original envelope json>
```

Rules for safe replay:

1. Stop or confirm the root cause is fixed before replaying; otherwise the
   event will simply fail again.
2. Replay one event (or one small batch) first and verify the resulting state
   before continuing.
3. Do not delete `processed_events` (consumer dedupe) or
   `processed_webhook_events` (Stripe webhook dedupe). Replays must be harmless
   by design.
4. Confirm no duplicate notifications or duplicate payment transitions were
   created after the replay.

### 5.3 Consumer offsets

New consumer groups start at `earliest` (`auto-offset-reset: earliest`), but
existing groups keep their committed offsets. To reprocess already-committed
messages, stop that consumer first, then reset its offsets for a specific topic:

```powershell
docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-service --describe
docker compose -f infra/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-service --topic orders.v1 --reset-offsets --to-earliest --execute
```

Groups: `order-service`, `payment-service`, `notification-service`. Resetting
offsets while a consumer is running is ineffective and can cause gaps. Prefer a
targeted DLQ replay over a whole-topic rewind.

### 5.4 Uncertain payments

Never start a second payment when the provider outcome is unknown. The payment
reconciliation job queries the provider for stale `PENDING` attempts and pending
refunds (`PAYMENT_RECONCILE_INTERVAL_MS`, `PAYMENT_RECONCILE_STALE_MS`). With the
Stripe adapter, follow `docs/stripe-manual-verification.md` section 7.

## 6. Log locations

- Local jar services: `logs/<service>.log` and `logs/<service>.err.log` for
  discovery-server, auth-service, catalog-service, order-service,
  payment-service, notification-service and api-gateway.
- Stack PIDs: `logs/stack-pids.txt` (written by `start-local-stack.ps1`, consumed
  by `stop-local-stack.ps1`).
- Tail a service log on Windows:

  ```powershell
  Get-Content -Tail 200 -Wait logs\payment-service.log
  Get-Content -Tail 200 -Wait logs\payment-service.err.log
  ```

- Tail on POSIX: `tail -f logs/payment-service.log`
- Infrastructure logs:

  ```powershell
  docker compose -f infra/docker-compose.yml logs -f kafka
  docker compose -f infra/docker-compose.yml logs --tail=200 postgres redis
  ```

- Look for correlation ids (`X-Correlation-Id`) to follow one request across
  gateway, service and event processing. Never log tokens, authorization headers
  or raw payment data.
