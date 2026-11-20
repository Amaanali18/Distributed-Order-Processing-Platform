# Frontend — Distributed Order & Payment Platform

React + Vite single-page application for the customer and administrator interfaces described in
PRD section 6.7. All API calls go through `src/api.js`; access and refresh tokens are kept in
`localStorage` (`dpp.accessToken`, `dpp.refreshToken`).

## Requirements

- Node.js 20.19+ or 22.12+ (Vite 8)
- npm
- A running API gateway (default `http://localhost:8080`) — see the repository root README for
  backend startup instructions.

## Install and run

```bash
cd frontend
npm install
npm run dev
```

The dev server listens on <http://localhost:5173>. `/api` requests are proxied to
`http://localhost:8080` with the `/api` prefix removed, matching the gateway routes.

## Build and preview

```bash
npm run build     # outputs frontend/dist
npm run preview   # serves the production build (default http://localhost:4173)
```

The Vite dev proxy does **not** apply to `npm run preview`. For a preview build that talks to a
separate gateway, set `VITE_API_BASE` to the full API base URL (and ensure the gateway allows CORS),
or serve `dist/` through a reverse proxy that maps `/api` to the gateway.

## Configuration

Create `frontend/.env.local` (not committed) if the defaults do not fit your environment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE` | `/api` | Base URL used by `src/api.js` for every request. |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | Dev-server proxy target for `/api`. |

Vite reads these variables at dev-server start and bakes them into the production bundle at build
time, so restart/rebuild after changing them.

## Features by role

Roles come from `GET /auth/me` (`roles`, e.g. `["ADMIN"]`). Administrator tabs are rendered only
when the signed-in user has `ADMIN` (or `ROLE_ADMIN`).

- **Customer:** registration/login, product catalogue, cart, checkout, order tracking, payment
  history with order links, refund status where reported, and notifications.
- **Admin:** product create/edit/status/restock, paginated order listing with status filter and
  detail expansion, payment inspection/refunds, an operational snapshot computed from the first
  page of the admin list endpoints, and notifications.

The operational dashboard intentionally reports sampled counts and status distributions from the
admin list APIs; it does not present Prometheus metrics.

## Simulated payment adapter

Payments are simulated — no real card data is collected or sent. In the Orders tab, choose
**Succeed** or **Fail** and press **Pay now**. The frontend creates (or reuses) a payment attempt
with a stable idempotency key `web-<orderId>`, then confirms it with the chosen `SUCCEEDED` or
`FAILED` outcome. Orders and payment history are refreshed after the action, and payment/refund
status is shown on the order card. Administrator refunds use the same simulated adapter and report
the refund status returned by the payment service.

## Quality checks

```bash
npm run lint      # oxlint
npm run build     # production build
```

Demo values shown on the login screen: `customer@dpp.local / CustomerPass123!`
(register this customer in the UI first — the backend only seeds the bootstrap
admin) and `admin@dpp.local / AdminPass123!` (seeded by the backend; configurable
via `DPP_BOOTSTRAP_ADMIN_EMAIL` / `DPP_BOOTSTRAP_ADMIN_PASSWORD`).
