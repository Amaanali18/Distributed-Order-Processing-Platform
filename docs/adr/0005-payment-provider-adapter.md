# ADR 0005: Payment provider behind an adapter

- Status: Accepted
- Date: 2026-10-15

## Context

The Payment Service integrates a provider in test mode. The provider choice may
change, and the platform must be demonstrable without a live provider.

## Decision

Define a `PaymentProvider` port in the Payment Service. Provide a
`SimulatedPaymentProvider` for the first end-to-end version and a
`StripePaymentProvider` adapter (test mode) for the real integration. The
provider is selected by configuration. Provider operations send idempotency
keys; webhooks are verified with the provider signing secret.

## Consequences

- The order-to-payment workflow can be completed and tested before a real
  provider account exists.
- Provider-specific types stay inside the adapter; the domain depends only on
  the port.
- Swapping providers means adding an adapter, not changing the workflow.
- Verification must cover both adapters: the simulated one for workflow tests,
  the Stripe one against a mock and against the provider test environment.
