# Load-test run record — TEMPLATE

> **WARNING: DO NOT COMMIT FABRICATED OR COPY/PASTED RESULTS.**
> Replace every placeholder below with values captured from an actual k6 run in
> this environment. Keep the raw `--summary-export` JSON and, where practical,
> the `--out json` request log next to this file. If you did not run the test,
> do not create a result file at all.

> **No results are claimed by this repository.** The scripts under `load-tests/k6/`
> are provided for reproducibility; no latency, throughput or error-rate numbers
> have been measured or asserted by the repository authors.

## Run identity

- Date/time (UTC): `<YYYY-MM-DDTHH:mm:ssZ>`
- Script: `<products.js | orders.js | payments.js>`
- Git commit under test: `<git rev-parse HEAD>`
- Operator: `<name or handle>`
- Environment: `<local jars + infra compose | docker compose full stack>`

## Command and options

```sh
# Exact command used, including environment variables
<k6 run --summary-export results/<name>-summary.json <script>.js>
```

- `GATEWAY_URL`: `<http://localhost:8080>`
- `VUS`: `<n>`
- `DURATION`: `<e.g. 30s>`
- `SLEEP_SECONDS`: `<n>`
- `P95_MS`: `<n>`
- `MAX_ERROR_RATE`: `<n>`
- `MIN_CHECK_RATE`: `<n>`
- Other overrides (`PRODUCT_STOCK`, `ORDER_QUANTITY`, ...): `<list or "none">`

## Environment details

- OS / CPU / RAM: `<from the machine that ran k6>`
- k6 version (`k6 version`): `<vX.Y.Z>`
- Java version (`java -version`): `<X>`
- Docker Compose version: `<v2.x.y>`
- Payment provider (`PAYMENT_PROVIDER`): `<simulated>` (must be simulated for these scripts)
- Gateway health before run: `<curl output>`

## Captured results (paste from k6 summary only)

| Metric | Value |
|---|---|
| `http_reqs` (total / rps) | `<...>` |
| `http_req_duration` avg | `<... ms>` |
| `http_req_duration` p(95) | `<... ms>` |
| `http_req_duration` p(99) | `<... ms>` |
| `http_req_failed` rate | `<... %>` |
| `checks` rate | `<... %>` |
| `iterations` | `<...>` |
| Threshold result (pass/fail) | `<...>` |
| k6 exit code | `<...>` |

## Artifacts

- Summary export: `results/<name>-summary.json`
- Raw request log: `results/<name>-requests.jsonl`
- Console log: `results/<name>-run.log`

## Observations

- Errors or anomalies: `<none / description>`
- Suspected bottleneck: `<none / description>`
- Follow-up action: `<...>`
