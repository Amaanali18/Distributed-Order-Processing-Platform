# Stripe test-mode manual verification

Scope: the real `StripePaymentProvider` adapter in test mode only.

> **NEVER COMMIT KEYS.** `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, Stripe CLI
> session tokens and any test-mode key must come from environment variables or a
> git-ignored `.env` file (see `.env.example`). Do not paste keys into this file,
> issues, chat, logs or screenshots. Stop and rotate a key immediately if it is
> exposed. The default local credentials in `.env.example` are development-only
> values, not Stripe credentials.
>
> **Automated tests do not use Stripe.** Per ADR 0005 and PRD §16, all automated
> tests and all load tests exercise the `SimulatedPaymentProvider` via
> `PAYMENT_PROVIDER=simulated`. This document is the manual, opt-in path for the
> Stripe adapter and is intentionally not part of CI.

## Prerequisites

- A Stripe account in **test mode** and the Stripe CLI installed and logged in
  (`stripe login`, `stripe version`).
- Docker Compose dependencies running and the stack built (see `docs/runbook.md`).
- A role that can set environment variables for `payment-service` and restart it.
- Familiarity with the simulated workflow in `infra/scripts/smoke-p2.ps1`.

## 1. Supply keys through the environment

Set keys in the shell that starts `payment-service`. Do not use `echo` on them
and do not write them to tracked files.

PowerShell (current session only):

```powershell
$env:PAYMENT_PROVIDER = 'stripe'
$env:STRIPE_API_KEY = '<test-mode secret key from your secret store>'
$env:STRIPE_WEBHOOK_SECRET = '<whsec_... printed by stripe listen>'
$env:STRIPE_API_BASE = 'https://api.stripe.com'
```

POSIX:

```sh
export PAYMENT_PROVIDER=stripe
export STRIPE_API_KEY='<test-mode secret key from your secret store>'
export STRIPE_WEBHOOK_SECRET='<whsec_... printed by stripe listen>'
export STRIPE_API_BASE=https://api.stripe.com
```

`payment-service` reads these from `dpp.payment.stripe.*`. Optional timeouts are
`STRIPE_CONNECT_TIMEOUT_MS` (default 2000) and `STRIPE_READ_TIMEOUT_MS`
(default 5000). Never put the secret key in the command line of a process that
other users can inspect.

## 2. Start webhook forwarding and capture the signing secret

```sh
stripe listen --forward-to localhost:8080/webhooks/stripe
```

The CLI prints a local signing secret beginning `whsec_...`. Put that value in
`STRIPE_WEBHOOK_SECRET` before starting `payment-service`; it must match the
secret used by the CLI for the forwarded events. The gateway routes
`/webhooks/**` publicly (unauthenticated), and the service accepts only requests
with a valid `Stripe-Signature` header.

Keep the `stripe listen` process running for the whole exercise. It logs each
delivered event id (`evt_...`), status code and response.

## 3. Start the stack with the Stripe adapter

Start the dependencies and the other services as described in `docs/runbook.md`,
but ensure the `payment-service` process has the variables from step 1. The
`start-local-stack.ps1` helper sets `PAYMENT_PROVIDER=simulated` for
payment-service. Do **not** run two payment-services on port 8084. Either:

1. run `start-local-stack.ps1`, then stop only the payment-service PID from
   `logs/stack-pids.txt` and start the payment-service jar again in a shell with
   the Stripe environment from step 1; or
2. start the other services individually without that helper and start
   payment-service with the Stripe environment.

Verify in the payment-service log that the Stripe adapter is selected (the
`provider` recorded on payment attempts must be `stripe`).

## 4. Create an order and a PaymentIntent through the service

All requests go through the gateway at `http://localhost:8080`. Use the
bootstrap admin to create a product, then a customer to create the order and
payment. Example payloads (replace tokens and ids from responses):

```http
POST /auth/login
Content-Type: application/json

{"email":"admin@dpp.local","password":"AdminPass123!"}
```

```http
POST /admin/products
Authorization: Bearer <admin accessToken>
Content-Type: application/json

{"sku":"STRIPE-MANUAL-001","name":"Manual Stripe check","description":"Manual test only","price":12.50,"currency":"USD","totalStock":10}
```

```http
POST /auth/register
Content-Type: application/json

{"email":"stripe-manual-<unique>@dpp.local","password":"Passw0rd!"}
```

```http
POST /orders
Authorization: Bearer <customer accessToken>
Content-Type: application/json

{"items":[{"productId":"<product id from admin response>","quantity":1}]}
```

```http
POST /payments
Authorization: Bearer <customer accessToken>
Idempotency-Key: stripe-manual-<unique value>
Content-Type: application/json

{"orderId":"<order id>","amount":12.50,"currency":"USD"}
```

`StripePaymentProvider.createPayment()` calls Stripe `POST /v1/payment_intents`
with the amount in minor units, lowercase currency, automatic payment methods,
`metadata[orderId]`, `metadata[customerId]`, and forwards the request's
`Idempotency-Key` as the provider idempotency key. The service persists a
`PENDING` attempt and returns `providerPaymentId` (`pi_...`). Confirm:
- the attempt is `PENDING` in `GET /payments/{id}`;
- the PaymentIntent exists in the Stripe **test mode** dashboard with the
  expected amount and metadata;
- no raw card data appears anywhere in the repository or logs.

## 5. Complete the test payment and verify signed webhooks

Complete the PaymentIntent with a Stripe test payment method (for example
`pm_card_visa` via Stripe CLI `stripe payment_intents confirm --help` for your
installed version, or through the test-mode dashboard). Alternatively send a
signed test event with `stripe trigger payment_intent.succeeded`; note that the
trigger creates its own PaymentIntent, so prefer an event for the `pi_...`
created in step 4 when checking a specific attempt.

Observe the webhook flow:

1. `StripeWebhookController` maps `payment_intent.succeeded` to success and
   `payment_intent.payment_failed` / `payment_intent.canceled` to failure.
2. Signature is verified over the **raw request body** with
   `STRIPE_WEBHOOK_SECRET`. An invalid signature returns HTTP 400 and logs
   `Rejected Stripe webhook with invalid signature`.
3. The event id (`evt_...`) is claimed in `processed_webhook_events` with status
   `PROCESSING`. On successful handling the row becomes `PROCESSED`; duplicate
   deliveries are acknowledged but ignored. If handling fails the row becomes
   `FAILED`, and a provider redelivery reclaims it. A concurrent duplicate while
   the first is still running returns HTTP 409 so it will be redelivered rather
   than dropped.
4. The attempt moves `PENDING -> SUCCESS` (or `FAILED`), an audit transition is
   written, and `PaymentSucceeded` (or `PaymentFailed`) is published through the
   outbox to `payments.v1`.
5. Order service commits or releases the reservation and moves the order to
   `CONFIRMED` or `FAILED`.

Check the `stripe listen` output for HTTP 200 and the service log for the state
change. Then check `GET /payments/{id}` and `GET /orders/{id}`.

### Negative signature check (no key required)

```sh
curl -i -X POST http://localhost:8080/webhooks/stripe \
  -H 'Content-Type: application/json' \
  -H 'Stripe-Signature: t=1,v1=deadbeef' \
  --data '{"id":"evt_test_invalid","type":"payment_intent.succeeded","data":{"object":{"id":"pi_unknown"}}}'
```

Expected: HTTP 400, no state change, warning in the payment-service log.

## 6. Duplicate delivery

1. Copy an `evt_...` id from the `stripe listen` output or Stripe dashboard.
2. Resend it: `stripe events resend evt_...` (repeat once more).
3. Confirm each delivery gets HTTP 200, but:
   - only one `payment_attempt_transitions` row is added for the settlement;
   - only one `OrderConfirmed` notification is created (`GET /notifications`);
   - `processed_webhook_events` contains one row for that `evt_...`.

Downstream consumers additionally deduplicate on the Kafka `eventId` in
`processed_events`. Do not delete deduplication rows to "force" a reprocess.

> `processed_webhook_events.status` is `PROCESSED` only after successful domain
> handling. Use the resend steps above to confirm that a duplicate delivery
> creates neither a second settlement transition nor a second notification.

## 7. Reconciliation of an uncertain outcome

Never initiate a second charge when the provider result is unknown.

1. Create a PaymentIntent through the service and leave the attempt `PENDING`.
2. Wait longer than `PAYMENT_RECONCILE_STALE_MS` (default 120000 ms). The job
   `PaymentReconciliationJob` runs every `PAYMENT_RECONCILE_INTERVAL_MS`
   (default 60000 ms) and logs `Reconciling N stale payment attempt(s)`.
3. `PaymentService.reconcile()` calls `GET /v1/payment_intents/{id}` and settles
   the attempt from the provider's actual status. No new PaymentIntent is made.
4. Repeat with a pending refund: `reconcileRefunds()` checks
   `GET /v1/refunds/{id}` and completes or reverts `REFUND_PENDING` attempts.

Check that the attempt reaches the correct final status and that exactly one
settlement transition is recorded.

## 8. Failure, late success and refund checks

- Send `payment_intent.payment_failed` for the `pi_...` under test and check
  `FAILED` payment and `FAILED` order plus released stock.
- Cancel an order with a still-`PENDING` attempt, then settle the PaymentIntent
  successfully. The payment layer issues a full refund (reason
  `late_success_after_termination`); see the compensation path in
  `docs/architecture/diagrams/sequence-order-payment.mmd`.
- Trigger an admin refund with `POST /admin/payments/{id}/refund` and check
  `RefundPending`, `RefundCompleted` and the resulting order status.

## 9. Cleanup and record

- Stop `stripe listen` (`Ctrl+C`) and restart the local stack with
  `PAYMENT_PROVIDER=simulated` so automated tests and load tests remain
  deterministic.
- Clear the shell variables (`Remove-Item Env:STRIPE_API_KEY` on PowerShell,
  `unset STRIPE_API_KEY` on POSIX). Do not leave secrets in shell history or
  process listings.
- Record only observations: date, event ids, PaymentIntent ids (these are not
  secrets), status codes and outcomes. Never record API keys or webhook secrets.
