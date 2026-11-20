# k6 load tests

Reproducible load tests for the key APIs, as required by PRD §9 (Performance)
and §16 (Implementation decisions). All scripts drive the public API gateway at
`http://localhost:8080`. They use the simulated payment adapter (the local stack
default, `PAYMENT_PROVIDER=simulated`); they never contact Stripe. Stripe test
mode is a separate manual procedure: `docs/stripe-manual-verification.md`.

> **No results are claimed in this repository.** The scripts and the reporting
> template are provided so that someone can run them and record actual numbers.
> See `results/TEMPLATE.md`.

## Scripts

| Script | Endpoint exercised | What it proves |
|---|---|---|
| `products.js` | `GET /products?page&size` | Public catalogue read path through the gateway and its p95/error rate under load. |
| `orders.js` | `POST /orders` | Authenticated order creation with server-side pricing and stock reservation (Feign call to catalog-service). |
| `payments.js` | `POST /payments` | Authenticated, idempotent payment-attempt creation through the simulated provider adapter via the gateway. |

Each `setup()` logs in as the bootstrap admin, registers a unique customer,
logs in as that customer and creates a dedicated product with admin rights. The
token and product id are returned to `default()`; no credentials are stored in
the scripts beyond the documented local bootstrap defaults.

## Prerequisites

1. [Install k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)
   (v0.50+ recommended). Verify with `k6 version`.
2. JDK 21 and Docker with Compose v2 (the repo provides `mvnw` / `mvnw.cmd`).
3. Start the dependencies and the stack from the repository root:

   ```powershell
   docker compose -f infra/docker-compose.yml up -d
   .\mvnw.cmd -q -DskipTests package
   powershell -ExecutionPolicy Bypass -File infra\scripts\start-local-stack.ps1
   ```

   POSIX equivalent:

   ```sh
   docker compose -f infra/docker-compose.yml up -d
   ./mvnw -q -DskipTests package
   ./infra/scripts/start-local-stack.ps1   # requires pwsh on POSIX
   ```

4. Confirm the gateway is healthy: `curl http://localhost:8080/actuator/health`.
5. Access tokens expire after 15 minutes (`dpp.jwt.access-token-ttl: 15m`). Keep
   each run shorter than that, or re-authenticate in a longer-running scenario.

## Running the scripts

Run commands from `load-tests/k6` (or prefix the script path with
`load-tests/k6/` when running from the repository root).

### Windows PowerShell

```powershell
cd load-tests\k6
k6 run products.js
k6 run orders.js
k6 run payments.js
```

With overrides:

```powershell
$env:GATEWAY_URL = 'http://localhost:8080'
$env:VUS = '10'
$env:DURATION = '1m'
$env:P95_MS = '800'
$env:MAX_ERROR_RATE = '0.02'
k6 run orders.js
```

### POSIX (Linux/macOS)

```sh
cd load-tests/k6
k6 run products.js
k6 run orders.js
k6 run payments.js
```

With overrides:

```sh
GATEWAY_URL=http://localhost:8080 VUS=10 DURATION=1m P95_MS=800 MAX_ERROR_RATE=0.02 \
  k6 run orders.js
```

## Environment variables

All variables are optional; defaults are shown.

| Variable | Default | Meaning |
|---|---|---|
| `GATEWAY_URL` | `http://localhost:8080` | Base URL of the API gateway. |
| `VUS` | `5` | Number of concurrent virtual users. |
| `DURATION` | `30s` | k6 test duration (for example `30s`, `2m`). |
| `P95_MS` | `500` (products), `1000` (orders/payments) | p95 latency gate in milliseconds. |
| `MAX_ERROR_RATE` | `0.05` | Maximum allowed fraction of failed HTTP requests (5%). |
| `MIN_CHECK_RATE` | `0.99` | Minimum allowed fraction of passing `check()` assertions (99%). |
| `SLEEP_SECONDS` | `1` | Pause between iterations, per VU. |
| `ADMIN_EMAIL` | `admin@dpp.local` | Bootstrap admin for setup (product creation). |
| `ADMIN_PASSWORD` | `AdminPass123!` | Bootstrap admin password for setup. |
| `CUSTOMER_PASSWORD` | `Passw0rd!` | Password for the unique customer registered by setup. |
| `PAGE_SIZE` | `20` | `size` query parameter for `products.js`. |
| `PRODUCT_PRICE` | `12.5` | Price of the synthetic product used by orders/payments. |
| `PRODUCT_STOCK` | `100000` | Stock of the synthetic product; raise for long order/payment runs. |
| `ORDER_QUANTITY` | `1` | Quantity per line item. |
| `CURRENCY` | `USD` | Currency used by `payments.js` (3 letters). |

Do not put real secrets in these variables or in scripts. The bootstrap admin
credentials above are the documented local-only defaults from `.env.example`.

## Saving results

Export a machine-readable summary and keep the text output next to it. Use a new
file name per run (never overwrite a previous measurement).

PowerShell:

```powershell
k6 run --summary-export results/products-summary.json products.js 2>&1 |
  Tee-Object -FilePath results/products-run.log
k6 run --out json=results/products-requests.jsonl products.js
```

POSIX:

```sh
k6 run --summary-export results/products-summary.json products.js 2>&1 |
  tee results/products-run.log
k6 run --out json=results/products-requests.jsonl products.js
```

Use `results/TEMPLATE.md` as the basis for a human-readable run note. Commit only
real, captured output; see the warning in that template.

## What the thresholds mean

Thresholds are pass/fail gates evaluated by k6 at the end of the run, not claims
about current performance:

- `http_req_duration: p(95)<P95_MS` — at least 95% of HTTP requests must finish
  below `P95_MS` milliseconds. A higher value means the tail latency is worse
  than the configured target.
- `http_req_failed: rate<MAX_ERROR_RATE` — no more than `MAX_ERROR_RATE` of
  requests may fail (non-2xx/3xx responses or transport errors). Note that 4xx
  responses from idempotency misuse or expired tokens count as failures.
- `checks: rate>MIN_CHECK_RATE` — at least `MIN_CHECK_RATE` of the explicit
  status/response-shape assertions must pass. A low rate usually means the API
  contract changed or setup/auth failed.

When a threshold is crossed k6 exits with a non-zero status (typically `99`),
which makes the scripts usable as CI gates. Use `k6 run --throw` to stop early on
errors. Record the actual environment, options and output; do not adjust a
threshold after a failed run just to make it pass without documenting why.

## Cleanup

Orders created by `orders.js` and `payments.js` stay PENDING; their stock
reservations are released by the catalog expiry sweep (default 60s). To remove
all synthetic state, reset the local stack as described in `docs/runbook.md`.
