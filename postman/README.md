# DPP Postman artifacts (local-only)

These files are **local API contract artifacts**. Nothing here was pushed,
published, or shared with a Postman workspace. The collection has been linted
and run locally against the live simulated Docker stack with the Postman CLI.

## Files

| Path | Purpose |
| --- | --- |
| `specs/dpp-api.yaml` | OpenAPI 3.0 contract derived from the implemented controllers, DTOs, gateway routes and security rules. Covers auth, catalog (public/admin/internal inventory), orders, payments (including admin and Stripe webhook), notifications and gateway health. |
| `collections/DPP API/` | Postman Collection v3 directory generated from the spec, then organized into service folders and extended with variables, auth overrides and `afterResponse` assertions. |
| `collections/DPP API/.resources/definition.yaml` | Collection metadata, variables and default bearer auth. All values are local placeholders. |
| `collections/DPP API/<service>/.resources/definition.yaml` | Folder-level auth and ordering: public folders use `noauth`, ADMIN folders use `{{adminToken}}`, internal folders use `X-Internal-Service-Token: {{internal_token}}`. `Cleanup` runs last so order cancellation does not break the payment flow. |
| `environments/local.environment.yaml` | Local environment with gateway URL, direct service URLs for non-gateway endpoints, placeholders and sample ids. |
| `skills/` | Plugin-managed local skill files (`postman skills update` was run; no workspace changes were made). |

## Validation performed

All commands were run from the repository root with the full Docker stack healthy
and `PAYMENT_PROVIDER=simulated`.

| Command | Result |
| --- | --- |
| `postman skills status` | Skills reported current. |
| `postman spec lint postman/specs/dpp-api.yaml --issueType=syntax --no-report-events` | **Passed** — `No issues found.` |
| `postman collection lint "postman/collections/DPP API"` | **Passed** — `No issues found.` (38 entities, 0 errors/warnings). |
| `postman environment lint postman/environments` | **Passed** — `No issues found.` (1 environment, 0 errors/warnings). |
| `postman collection run "postman/collections/DPP API" -e "postman/environments/local.environment.yaml" --env-var "admin_password=<local-admin-password>" --env-var "internal_token=<local-internal-token>" --env-var "idempotency_key=<unique-key>" --no-report-events --timeout-request 30000` | **Passed** — 1 iteration, 38 requests, 2 pre-request scripts, 9 test scripts, 9 assertions, 0 failed; exit code 0. Average response time 43 ms. |
| `postman spec lint postman/specs/dpp-api.yaml --no-report-events` | Not completed: governance linting requires `postman login`. Login/publishing is out of scope. |

The live run exercised the simulated payment path end to end: customer
registration/login, admin login, direct JWKS, product listing and admin product
operations, order creation, payment creation/confirm/refund, notification lists,
the no-op Stripe webhook and gateway health. The CLI printed
`No authorization data found. Please use the postman login command.` because the
session is a guest, but it still executed the local collection successfully.
No `--output` report was requested because that option requires login.

Postman MCP tools were not exposed in this agent's active toolset during this
work: tool discovery found no `postman`/`getEnabledTools`/`getAuthenticatedUser`
operations, and `list_mcp_resources` returned no resources. The local CLI was
used instead. No workspace push, documentation publish or mock publish occurred.

## Import and run locally

1. In Postman, import the folder `postman/collections/DPP API/` as a collection
   (v3 directory format) and `postman/environments/local.environment.yaml` as the
   `Local` environment.
2. With the stack running, execute the command in the validation table. Only the
   admin password, internal service token and a unique idempotency key need
   overriding; the register request's pre-request script generates a fresh
   customer email/password for each run.
3. `{{auth_service_url}}`, `{{catalog_service_url}}` and `{{order_service_url}}`
   point directly at auth/catalog/order because JWKS and `/internal/**` are not
   gateway routes. `Cleanup/Cancel an order` runs last; after a successful
   payment the order is already confirmed, so that unasserted request may return
   409. The inventory sample requests use fixed example ids and may return
   409/404 when run without seeded data; they carry no assertions.

The webhook sample uses an unhandled event type (`ping`) and is intentionally a
no-op against a running stack.

## Regenerating

The collection was generated with the CLI and then edited by hand. Regenerating
from the spec will overwrite those edits:

```powershell
postman spec generate collection postman/specs/dpp-api.yaml -n "DPP API" --folder-strategy Tags --force
```

After regenerating, reapply the collection/folder definitions, variable-driven
bodies, pre-request scripts, test scripts and folder ordering, then re-run the
lint and collection commands above.

## Contract gaps kept out of the spec

- Exact Spring Data `Page` serialization fields beyond the stable metadata used
  here; implementation-specific fields may also be present.
- Actuator health component details (configuration-dependent).
- Any Stripe webhook fields the controller does not read, and provider-specific
  signature behavior.
- Any status code or response field not derivable from the controllers, DTOs,
  exception handler, security config or gateway config.
