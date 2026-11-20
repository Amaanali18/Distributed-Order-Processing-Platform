/**
 * k6 load test: product catalogue listing (GET /products) through the gateway.
 *
 * Target:   http://localhost:8080 (override with GATEWAY_URL).
 * Auth:     setup() logs in as the bootstrap admin, registers a unique customer
 *           and logs in as that customer. The customer token from setup data is
 *           sent on every request below. (GET /products is public in this repo,
 *           but sending the token keeps one auth pattern across all three scripts.)
 * Scope:    This script does not create payments. It exercises the read path of
 *           the catalogue. The simulated payment adapter is only exercised by
 *           payments.js; all traffic in these scripts goes through the gateway.
 * Provider: PAYMENT_PROVIDER=simulated is the default for the local stack.
 *
 * No latency, throughput or error-rate numbers are claimed here. Thresholds are
 * gates chosen by whoever runs the test; see README.md and results/TEMPLATE.md.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@dpp.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'AdminPass123!';
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || 'Passw0rd!';
const PAGE_SIZE = __ENV.PAGE_SIZE || '20';
const PRODUCT_PRICE = Number(__ENV.PRODUCT_PRICE || 12.5);
const PRODUCT_STOCK = Number(__ENV.PRODUCT_STOCK || 100000);

const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '30s';
const P95_MS = Number(__ENV.P95_MS || 500);
const MAX_ERROR_RATE = Number(__ENV.MAX_ERROR_RATE || 0.05);
const MIN_CHECK_RATE = Number(__ENV.MIN_CHECK_RATE || 0.99);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

export const options = {
  vus: VUS,
  duration: DURATION,
  setupTimeout: '2m',
  thresholds: {
    // p95 of all HTTP request durations; not a measured value, a pass/fail gate.
    'http_req_duration': [`p(95)<${P95_MS}`],
    // Fraction of requests with status >= 400 or a transport error.
    'http_req_failed': [`rate<${MAX_ERROR_RATE}`],
    // Fraction of check() assertions that pass.
    'checks': [`rate>${MIN_CHECK_RATE}`],
  },
  tags: { workload: 'products', gateway: BASE_URL },
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
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return headers;
}

export function setup() {
  const adminLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() },
  );
  if (!check(adminLogin, {
    'setup: admin login 200': (r) => r.status === 200 && !!(body(r) || {}).accessToken,
  })) {
    throw new Error(`setup admin login failed: status=${adminLogin.status} body=${adminLogin.body}`);
  }
  const adminToken = body(adminLogin).accessToken;

  const email = `k6-products-${randomHex(10)}@dpp.local`;
  const registered = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ email, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() },
  );
  if (!check(registered, { 'setup: customer register 201': (r) => r.status === 201 })) {
    throw new Error(`setup customer register failed: status=${registered.status} body=${registered.body}`);
  }

  const customerLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() },
  );
  if (!check(customerLogin, {
    'setup: customer login 200': (r) => r.status === 200 && !!(body(r) || {}).accessToken,
  })) {
    throw new Error(`setup customer login failed: status=${customerLogin.status} body=${customerLogin.body}`);
  }
  const customerToken = body(customerLogin).accessToken;

  const sku = `K6-PRODUCTS-${randomHex(8).toUpperCase()}`;
  const product = http.post(
    `${BASE_URL}/admin/products`,
    JSON.stringify({
      sku,
      name: 'k6 load-test widget',
      description: 'Synthetic product created by load-tests/k6/products.js',
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
  const res = http.get(
    `${BASE_URL}/products?page=0&size=${PAGE_SIZE}`,
    { headers: jsonHeaders(data.customerToken) },
  );

  check(res, {
    'GET /products returns 200': (r) => r.status === 200,
    'page has content array': (r) => Array.isArray((body(r) || {}).content),
    'page has pagination metadata': (r) => typeof (body(r) || {}).totalElements === 'number',
  });

  sleep(SLEEP_SECONDS);
}
