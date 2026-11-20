# Product Requirements Document (PRD)
## Distributed Order & Payment Processing Platform

**Version:** 1.0  
**Project Type:** Full-stack distributed application  
**Frontend:** React.js  
**Backend:** Java, Spring Boot, Spring Cloud  
**Infrastructure:** PostgreSQL, Kafka, Redis, Docker  
**Payment Provider:** Razorpay or Stripe (test mode)

---

## 1. Project Overview

Build a full-stack order and payment processing platform using a microservices architecture. The system must support product browsing, inventory management, order creation, payment processing, transaction history, and administrative operations.

The project aims to demonstrate practical knowledge of microservices, distributed systems, secure payment workflows, asynchronous messaging, fault tolerance, and production-style monitoring.

This is a portfolio and educational project, not a production financial platform.

## 2. Project Objectives

- Build independently deployable Spring Boot microservices.
- Implement service discovery and centralized API routing.
- Develop a React frontend integrated with backend APIs.
- Integrate a payment provider in test mode.
- Prevent duplicate payment processing.
- Implement asynchronous event-driven communication.
- Handle service failures and recover incomplete workflows.
- Add authentication, authorization, monitoring, and automated testing.
- Document architectural decisions and demonstrate system behavior.

## 3. Technology Stack

### Frontend
- React.js
- JavaScript
- HTML5 and CSS3
- Axios or Fetch API
- React Router

### Backend
- Java
- Spring Boot
- Spring Data JPA
- Spring Security
- JWT authentication
- Spring Cloud Gateway
- Netflix Eureka
- OpenFeign
- Resilience4j

### Data and Messaging
- PostgreSQL
- Apache Kafka
- Redis

### DevOps and Observability
- Docker
- Docker Compose
- Spring Boot Actuator
- Prometheus
- Grafana
- Swagger/OpenAPI
- JUnit 5 and Testcontainers

## 4. System Architecture

The system consists of the following components.

### 4.1 API Gateway
- Serve as the single entry point for frontend requests.
- Route requests to the appropriate backend services.
- Apply authentication and authorization policies.
- Support rate limiting and consistent error responses.

### 4.2 Eureka Server
- Register and discover application services.
- Support service-to-service communication through registered instances.

### 4.3 Auth/User Service
- Register and authenticate users.
- Manage customer and administrator roles.
- Issue and validate JWTs.
- Restrict administrative operations to authorized users.

### 4.4 Product/Inventory Service
- Maintain product details, prices, and stock.
- Validate product availability.
- Reserve stock when an order is initiated.
- Release reservations after eligible failures or expiration.
- Prevent overselling under concurrent requests.

### 4.5 Order Service
- Create and retrieve customer orders.
- Maintain order items, quantities, prices, and totals.
- Track order lifecycle and payment outcome.
- Publish order-related events.
- Initiate recovery or cancellation when payment fails.

### 4.6 Payment Service
- Create payment attempts associated with valid orders.
- Integrate with a provider's test environment.
- Verify payment status and signed webhooks.
- Maintain transaction history.
- Prevent duplicate processing using idempotency.
- Support refunds and payment reconciliation.

### 4.7 Notification Service
- Consume relevant order and payment events.
- Send email notifications or provide a development notification log.
- Avoid duplicate notifications when messages are redelivered.

### 4.8 Kafka
- Transport asynchronous order, payment, inventory, and notification events.
- Support consumer groups and retry handling.
- Route repeatedly failing messages to dead-letter topics.
- Support safe replay and idempotent consumption.

## 5. Data Ownership

Each business service must own its data and business rules.

- Auth/User Service: users and roles.
- Product/Inventory Service: products, stock, and reservations.
- Order Service: orders and order items.
- Payment Service: payment attempts, transactions, and refunds.
- Notification Service: notification records and delivery status.

Separate PostgreSQL databases are preferred. Separate schemas may be used for local development if necessary. Services must not directly modify another service's tables.

Use database migrations such as Flyway for schema management.

## 6. Functional Requirements

### 6.1 Authentication and Authorization
- Users can register and log in.
- Protected endpoints require valid authentication.
- Customers can access only their own orders and payment history.
- Administrators can manage products and inspect system transactions.
- Secrets must never be committed to source control.

### 6.2 Product and Inventory Management
- List and retrieve products.
- Create, update, and deactivate products through authorized admin APIs.
- Validate stock before accepting an order.
- Reserve inventory atomically.
- Prevent concurrent requests from reserving unavailable stock.
- Release stock when an order is cancelled or a payment fails, according to defined workflow rules.

### 6.3 Order Management
- Create an order from validated product IDs and quantities.
- Calculate totals using server-side prices.
- Store the price snapshot used when the order was placed.
- Retrieve order details and history.
- Support order statuses:
  - PENDING
  - CONFIRMED
  - FAILED
  - CANCELLED
  - REFUND_PENDING
  - REFUNDED

- Validate permitted state transitions.
- Ensure an order is not confirmed before successful payment verification.

### 6.4 Payment Processing
- Create a payment attempt for an eligible order.
- Initiate provider checkout in test mode.
- Verify payment results server-side.
- Validate webhook signatures and reject invalid requests.
- Track payment states:
  - PENDING
  - SUCCESS
  - FAILED
  - REFUND_PENDING
  - REFUNDED

- Support payment history and refunds.
- Handle repeated requests and duplicate webhook deliveries safely.
- Never store raw card details, CVV, or sensitive payment credentials.

### 6.5 Event-Driven Processing
Define versioned events such as:
- OrderCreated
- InventoryReserved
- PaymentSucceeded
- PaymentFailed
- OrderConfirmed
- OrderCancelled
- OrderFailed
- RefundPending
- RefundCompleted

Each event should include an event ID, event type, schema version, aggregate ID, timestamp, and relevant business metadata.

Consumers must handle duplicate events safely. Important state changes should use a transactional outbox where appropriate.

### 6.6 Failure Handling
- Configure connection and response timeouts.
- Apply bounded retries with exponential backoff for transient failures.
- Use Resilience4j circuit breakers for suitable remote calls.
- Handle unavailable services without falsely reporting success.
- Implement dead-letter topics for repeatedly failing messages.
- Provide safe recovery and replay procedures.
- Reconcile uncertain payment outcomes with the provider instead of blindly initiating another payment.

### 6.7 Frontend
Build a responsive React application containing:

**Customer interface**
- Registration and login.
- Product catalogue.
- Shopping cart.
- Order creation and checkout.
- Order status tracking.
- Payment history.
- Refund status where applicable.

**Administrator interface**
- Product and stock management.
- Order listing and inspection.
- Payment and refund inspection.
- Basic operational status dashboard.

The frontend must display loading states, validation errors, payment failures, and success confirmations.

## 7. Critical Business Workflow

1. A customer selects products and quantities.
2. The backend validates prices and stock.
3. Inventory is reserved.
4. An order is created in a pending state.
5. The Payment Service creates a payment attempt.
6. The customer completes provider checkout.
7. The backend verifies the result using trusted provider APIs or verified webhooks.
8. A successful payment is recorded and a payment event is published.
9. The order is confirmed and the inventory reservation is finalized.
10. A failed or expired payment triggers the appropriate recovery process.

Because the services use separate databases, this workflow must not assume a single atomic database transaction across the entire system.

Implement compensating actions for incomplete workflows. Define how late payment success is handled after an order has been cancelled or inventory has been released.

## 8. Idempotency and Financial Integrity

- Accept an idempotency key for payment-initiating operations.
- Persist the key with the associated operation and outcome.
- Enforce uniqueness using database constraints.
- Return the original outcome for a repeated identical request.
- Reject reuse of a key with a different request payload.
- Use provider-supported idempotency keys for external payment operations where available.
- Deduplicate webhook events and Kafka messages.
- Use explicit transaction boundaries for local database updates.
- Maintain auditable payment state transitions.

A Redis lock alone is not sufficient to guarantee financial correctness. Durable database constraints and provider-side protections are required.

## 9. Non-Functional Requirements

### Security
- JWT-based authentication and role-based authorization.
- Server-side ownership checks for orders and transactions.
- Input validation and safe error responses.
- Secure webhook signature verification.
- Secrets supplied through environment configuration.
- No sensitive payment data in application logs.

### Reliability
- Health and readiness endpoints.
- Timeouts and bounded retries.
- Idempotent operations and consumers.
- Defined recovery behavior for incomplete orders.
- Graceful handling of service and broker outages.

### Performance
- Pagination for order, product, and transaction listings.
- Database indexes for common queries.
- Configurable load tests for key APIs.
- Report measured throughput, latency, and error rates without inventing performance claims.

### Maintainability
- Consistent naming and package structure.
- DTOs and centralized exception handling.
- Service-specific configuration.
- Database migrations.
- Automated tests and documented API contracts.

## 10. Observability

Use Spring Boot Actuator, Prometheus, and Grafana to expose and visualize:
- Request throughput.
- API latency.
- Error rates.
- Service health.
- JVM and database connection-pool metrics.
- Kafka consumer lag.
- Failed message counts.
- Payment and order state distributions.

Use correlation IDs across gateway requests, service calls, and event processing. Avoid logging secrets, tokens, or sensitive payment information.

## 11. Testing Requirements

### Unit Testing
Test business logic, state transitions, validation, and idempotency rules.

### Integration Testing
Test persistence, repository constraints, API contracts, and payment provider adapters.

### End-to-End Testing
Verify successful and failed order-to-payment workflows.

### Failure Testing
Demonstrate:
- Payment failure.
- Duplicate payment request.
- Duplicate webhook delivery.
- Duplicate Kafka event.
- Inventory contention.
- Payment or inventory service unavailability.
- Kafka outage and consumer recovery.
- Late payment success after an order timeout.

### Load Testing
Measure latency, throughput, and error rates for defined test workloads. Record test environment and results in the repository.

## 12. Deployment Requirements

- Provide Dockerfiles and Docker Compose configuration.
- Support startup of all required services and dependencies.
- Use environment variables for credentials and configuration.
- Include database migrations and health checks.
- Document startup, shutdown, reset, and troubleshooting commands.
- Provide a reproducible local demonstration.

## 13. Definition of Done

The project is complete when:

1. All services can be started using documented instructions.
2. Authentication and authorization work correctly.
3. Customers can browse products, place orders, and view history.
4. Test payments can succeed and fail correctly.
5. Orders are not falsely confirmed after failed or unverified payments.
6. Duplicate requests and events do not create duplicate business effects.
7. Inventory remains consistent under concurrent orders.
8. Service and broker failures have tested recovery behavior.
9. Administrators can inspect products, orders, and payment records.
10. Automated tests, API documentation, architecture diagrams, and README are included.
11. Metrics and load-test results can be reproduced.
12. The developer can explain the system's architecture, trade-offs, and failure handling.

## 14. Implementation Strategy

Implement in this order:

1. Architecture, API contracts, repository layout, and database ownership.
2. Eureka, API Gateway, and core business services.
3. Complete order-to-payment workflow using a simulated payment adapter.
4. React frontend and authentication.
5. Real payment-provider test integration and verified webhooks.
6. Kafka events and idempotent consumers.
7. Resilience4j, outbox, dead-letter queues, and recovery.
8. Docker, automated tests, monitoring, and load testing.
9. Documentation and final demonstration.

Do not introduce every technology at once. Each phase must be tested and integrated before the next begins.

## 15. Future Scope

Potential extensions include Saga orchestration, advanced reconciliation, distributed tracing, Kubernetes, CI/CD, multi-instance deployment, custom load-balancing experiments, and more sophisticated fraud/risk controls.

These are extensions, not substitutes for completing the core requirements.

**Final objective:** Deliver a complete, reproducible, testable distributed application that demonstrates sound engineering decisions and can be defended in a technical interview.

## 16. Implementation Decisions

These decisions resolve ambiguous points and are binding on the implementation:

- Rate limiting lives in the API Gateway and uses a Redis-backed token bucket so limits are shared across gateway instances.
- Load and performance testing is done with k6 using reproducible scripts checked into the repository.
- The observability scope is metrics only: every service exposes a metrics endpoint and the required external setup is documented. No Prometheus or Grafana containers are shipped, to keep local runtime lightweight.
- The full application runs through Docker Compose (all services plus infrastructure), so the stack can be started end to end with one command.
- Automated tests exercise the workflow exclusively through the simulated payment adapter. The Stripe test-mode end-to-end path is documented as a manual verification procedure to avoid committing provider secrets and creating flaky external dependencies.
- Payment status values are `PENDING`, `SUCCESS`, `FAILED`, `REFUND_PENDING`, and `REFUNDED`. Orders that fail payment transition to `FAILED`; an order whose refund is in flight transitions to `REFUND_PENDING` before `REFUNDED`.
- A payment that settles as successful after its order was cancelled or failed is automatically compensated with a full refund.
- Inventory held by a reservation that is never paid is released by a scheduled expiry sweep keyed on reservation status and expiry time.
