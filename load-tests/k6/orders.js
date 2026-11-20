/**
 * k6 load test: order creation (POST /orders) through the gateway.
 *
 * Target:   http://localhost:8080 (override with GATEWAY_URL).
 * Auth:     setup() logs in as the bootstrap admin, registers a unique customer,
 *           logs in as that customer and creates a product as admin. The customer
 *           token from setup data is used for every order in default().
 * Scope:    Each iteration creates one PENDING order with one line item, which
 *           reserves stock in catalog-service via the synchronous Feign call.
 *           No payments are confirmed here. The simulated payment adapter is
 *           exercised by payments.js; all requests go through the gateway.
 * Caution:  Orders remain PENDING until a payment settles or the order is
 *           cancelled; reservations are released by the expiry sweep
 *           (CATALOG_RESERVATION_SWEEP_INTERVAL_MS, default 60s). Set
 *           PRODUCT_STOCK high enough and keep runs short, or reset the stack.
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
  tags: { workload: 'orders', gateway: BASE_URL },
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

function jsonHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
}

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

  const email = `k6-orders-${randomHex(10)}@dpp.local`;
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

  const sku = `K6-ORDERS-${randomHex(8).toUpperCase()}`;
  const product = http.post(
    `${BASE_URL}/admin/products`,
    JSON.stringify({
      sku,
      name: 'k6 load-test widget',
      description: 'Synthetic product created by load-tests/k6/orders.js',
      price: PRODUCT_PRICE,
      currency: 'USD',
      totalStock: PRODUCT_STOCK,
    }),
    { headers: jsonHeaders(adminToken) },
  );
  if (!check(product, {
    'setup: admin creates product 201': (r) => r.status === 201 && !!body(r).id,
  })) {
    throw new Error(`setup product creation failed: status=${product.status} body=${product.body}`);
  }

  return { customerToken, adminToken, productId: body(product).id, sku };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/orders`,
    JSON.stringify({ items: [{ productId: data.productId, quantity: ORDER_QUANTITY }] }),
    { headers: jsonHeaders(data.customerToken) },
  );
  const json = body(res);

  check(res, {
    'POST /orders returns 201': (r) => r.status === 201,
    'order has an id': () => !!json && !!json.id,
    'new order is PENDING': () => !!json && json.status === 'PENDING',
    'order echoes one item': () => !!json && Array.isArray(json.items) && json.items.length === 1,
    'order total is numeric': () => !!json && typeof json.totalAmount === 'number',
  });

  sleep(SLEEP_SECONDS);
}
