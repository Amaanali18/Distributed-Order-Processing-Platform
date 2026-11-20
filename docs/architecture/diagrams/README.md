# Architecture diagrams

Mermaid source files. GitHub, GitLab, most Markdown viewers and the Mermaid
Live Editor render `.mmd` files directly; no external diagram tooling or build
step is required for this repository.

| File | Contents |
|---|---|
| `component.mmd` | Gateway, Eureka, the six Spring Boot services, per-service PostgreSQL databases, Redis, Kafka topics and the two payment-provider adapters. |
| `sequence-order-payment.mmd` | End-to-end order-to-payment happy path, failed-payment path and the cancellation plus late-success compensation path. |
| `state-order.mmd` | Order statuses: `PENDING`, `CONFIRMED`, `FAILED`, `CANCELLED`, `REFUND_PENDING`, `REFUNDED`. |
| `state-payment.mmd` | Payment statuses: `PENDING`, `SUCCESS`, `FAILED`, `REFUND_PENDING`, `REFUNDED`. |

## How to render

- GitHub: open the `.mmd` file in the web UI; Mermaid diagrams render natively.
- Mermaid Live Editor: paste the file contents with `Diagram type` set to
  Mermaid. This is optional and not required to review the diagrams.
- VS Code: a Mermaid preview extension is optional, not required.

## Accuracy notes

- Diagram content follows the current code, not just the PRD. Topic names come
  from `platform-common/.../Topics.java`; event names from `EventTypes.java`.
- Only `order-service` and `payment-service` publish through the transactional
  outbox. `notification-service` consumes `orders.v1` and `payments.v1`.
  `payment-service` consumes order termination events from `orders.v1`.
- The synchronous Feign calls are `reserve`, `commit` and `release` on
  `catalog-service` endpoints under `/internal/inventory/**`; those endpoints
  are not routed through the public gateway.
- Gateway rate limiting is shown as a decision from PRD §16 (Redis-backed token
  bucket). Verify the deployed configuration before relying on it; see
  `docs/testing.md` for the current gap list.
- The timeout/compensation flow is documented as intended behavior. The current
  implementation refunds a late-successful payment at the payment layer but
  does not transition a `CANCELLED` or `FAILED` order to `REFUNDED`. This gap is
  called out in the sequence and state diagrams.
- Payment provider: `simulated` is the default for local runs and all automated
  tests. The Stripe adapter is opt-in and verified manually; see
  `docs/stripe-manual-verification.md` and ADR 0005.
