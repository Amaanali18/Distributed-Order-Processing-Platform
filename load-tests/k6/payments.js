/**
 * k6 load test: payment creation (POST /payments) through the gateway.
 *
 * Target:   http://localhost:8080 (override with GATEWAY_URL).
 * IMPORTANT: The local stack runs PAYMENT_PROVIDER=simulated (ADR 0005). Every
 *            POST /payments and POST /payments/{id}/confirm below reaches the
 *            SimulatedPaymentProvider *through the API gateway*; no real Stripe
 *            call is made. Do not point this script at a stack configured with
 *            PAYMENT_PROVIDER=stripe. Stripe test mode is a manual procedure:
 *            see docs/stripe-manual-verification.md.
 * Auth:     setup() logs in as the bootstrap admin, registers a unique customer,
 *           logs in as that customer and creates a product as admin. The customer
 *           token from setup data creates one order per VU; each iteration then
 *           creates a payment with a fresh Idempotency-Key.
 * Idempotency: POST /payments requires a unique Idempotency-Key. Reusing a key
 *           with the same payload returns the original attempt; reusing it with
 *           a different payload is rejected. This script uses a fresh key per
 *           request. The confirm endpoint is exercised by infra/scripts/smoke-p2.ps1.
 *
 * No latency, throughput or error-rate numbers are claimed here. See README.md.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@dpp.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'AdminPass123!';
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || 'Passw0rd!';
const PRODUCT_PRICE = Number(__ENV.PRODUCT_PRICE || 12.5);
const PRODUCT_STOCK = Number(__ENV.PRODUCT_STOCK || 100000);
const ORDER_QUANTITY = Number(__ENV.ORDER_QUANTITY || 1);
const CURRENCY = __ENV.CURRENCY || 'USD';

const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '30s';
const P95_MS = Number(__ENV.P95_MS || 1000);
const MAX_ERROR_RATE = Number(__ENV.MAX_ERROR_RATE || 0.05);
const MIN_CHECK_RATE = Number(__ENV.MIN_CHECK_RATE || 0.99);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

export const options = {
  vus: VUS,
  duration: DURATION,
  setupTimeout: '2m',
  thresholds: {
    // p95 of all HTTP request durations; a pass/fail gate, not a measured result.
    'http_req_duration': [`p(95)<${P95_MS}`],
    'http_req_failed': [`rate<${MAX_ERROR_RATE}`],
    'checks': [`rate>${MIN_CHECK_RATE}`],
  },
  tags: { workload: 'payments', gateway: BASE_URL },
};

function body(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function randomHex(length) {
  let out = '';
  for (let i = 0; i < length; i++) {
    out += Math.floor(Math.random() * 16).toString(16);
  }
  return out;
}

function jsonHeaders(token, idempotencyKey) {
  const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return headers;
}

// setup() runs once. Each VU creates its own order lazily in default(), so
// payment attempts are spread over distinct orders and distinct idempotency keys.
export function setup() {
  const adminLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (!check(adminLogin, {
    'setup: admin login 200': (r) => r.status === 200 && !!(body(r) || {}).accessToken,
  })) {
    throw new Error(`setup admin login failed: status=${adminLogin.status} body=${adminLogin.body}`);
  }
  const adminToken = body(adminLogin).accessToken;

  const email = `k6-payments-${randomHex(10)}@dpp.local`;
  const registered = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ email, password: CUSTOMER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (!check(registered, { 'setup: customer register 201': (r) => r.status === 201 })) {
    throw new Error(`setup customer register failed: status=${registered.status} body=${registered.body}`);
  }

  const customerLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: CUSTOMER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (!check(customerLogin, {
    'setup: customer login 200': (r) => r.status === 200 && !!(body(r) || {}).accessToken,
  })) {
    throw new Error(`setup customer login failed: status=${customerLogin.status} body=${customerLogin.body}`);
  }
  const customerToken = body(customerLogin).accessToken;

  const sku = `K6-PAYMENTS-${randomHex(8).toUpperCase()}`;
  const product = http.post(
    `${BASE_URL}/admin/products`,
    JSON.stringify({
      sku,
      name: 'k6 load-test widget',
      description: 'Synthetic product created by load-tests/k6/payments.js',
      price: PRODUCT_PRICE,
      currency: CURRENCY,
      totalStock: PRODUCT_STOCK,
    }),
    { headers: jsonHeaders(adminToken) },
  );
  if (!check(product, {
    'setup: admin creates product 201': (r) => r.status === 201 && !!body(r).id,
  })) {
    throw new Error(`setup product creation failed: status=${product.status} body=${product.body}`);
  }

  return {
    customerToken,
    adminToken,
    productId: body(product).id,
    amount: Number((PRODUCT_PRICE * ORDER_QUANTITY).toFixed(2)),
    currency: CURRENCY,
  };
}

// Per-VU state: k6 reuses the same JS context for all iterations of a VU.
let vuOrderId = null;

export default function (data) {
  if (vuOrderId === null) {
    const order = http.post(
      `${BASE_URL}/orders`,
      JSON.stringify({ items: [{ productId: data.productId, quantity: ORDER_QUANTITY }] }),
      { headers: jsonHeaders(data.customerToken) },
    );
    if (!check(order, {
      'setup VU: order created 201 PENDING': (r) =>
        r.status === 201 && (body(r) || {}).status === 'PENDING' && !!(body(r) || {}).id,
    })) {
      // Without an order there is nothing to pay for; skip this iteration.
      return;
    }
    vuOrderId = body(order).id;
  }

  const idempotencyKey = `k6pay-${__VU}-${__ITER}-${randomHex(12)}`;
  const res = http.post(
    `${BASE_URL}/payments`,
    JSON.stringify({ orderId: vuOrderId, amount: data.amount, currency: data.currency }),
    { headers: jsonHeaders(data.customerToken, idempotencyKey) },
  );
  const json = body(res);

  check(res, {
    'POST /payments returns 201': (r) => r.status === 201,
    'payment has an id': () => !!json && !!json.id,
    'new payment is PENDING': () => !!json && json.status === 'PENDING',
    'payment links to order': () => !!json && json.orderId === vuOrderId,
    'simulated adapter assigned a provider id': () => !!json && !!json.providerPaymentId,
  });

  sleep(SLEEP_SECONDS);
}
