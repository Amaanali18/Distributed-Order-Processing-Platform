# Project Inventory

- **Project root:** `F:\IdeaProjects\Distributed Processing Platform`
- **Generated:** 2026-10-28 13:15:49
- **Files inventoried:** 228
- **Likely text files:** 221
- **Files excluded by basic safety/filter rules:** 2

## Top-level entries

- directory: `.git`
- directory: `.idea`
- directory: `.mvn`
- directory: `.postman`
- directory: `api-gateway`
- directory: `auth-service`
- directory: `catalog-service`
- directory: `discovery-server`
- directory: `docs`
- directory: `frontend`
- directory: `infra`
- directory: `logs`
- directory: `notification-service`
- directory: `order-service`
- directory: `payment-service`
- directory: `platform-common`
- directory: `platform-messaging`
- directory: `platform-testing`
- directory: `postman`
- file: `.editorconfig`
- file: `.env.example`
- file: `.gitignore`
- file: `AGENTS.md`
- file: `mvnw`
- file: `mvnw.cmd`
- file: `pom.xml`
- file: `PRD.md`
- file: `README.md`

## Detected manifests and project documents

- `AGENTS.md` (374 bytes)
- `api-gateway/pom.xml` (2297 bytes)
- `auth-service/pom.xml` (3382 bytes)
- `catalog-service/pom.xml` (3382 bytes)
- `discovery-server/pom.xml` (1408 bytes)
- `docs/architecture/README.md` (2963 bytes)
- `frontend/package.json` (459 bytes)
- `frontend/README.md` (1009 bytes)
- `infra/docker-compose.yml` (1996 bytes)
- `notification-service/pom.xml` (3708 bytes)
- `order-service/pom.xml` (4035 bytes)
- `payment-service/pom.xml` (3713 bytes)
- `platform-common/pom.xml` (1680 bytes)
- `platform-messaging/pom.xml` (1984 bytes)
- `platform-testing/pom.xml` (1248 bytes)
- `pom.xml` (3800 bytes)
- `PRD.md` (15332 bytes)
- `README.md` (3070 bytes)

## File categories

- configuration: 25
- database: 9
- documentation: 8
- frontend: 2
- other-text: 7
- project-doc: 5
- script: 5
- source: 167

## Git working-tree snapshot

``	ext
Branch: master
Changed/untracked entries: 68
Git status:
 M README.md
 M api-gateway/src/main/resources/application.yml
 M catalog-service/src/main/java/com/dpp/catalog/CatalogApplication.java
 M catalog-service/src/main/java/com/dpp/catalog/repository/ReservationRepository.java
 M catalog-service/src/main/java/com/dpp/catalog/service/InventoryService.java
 M catalog-service/src/main/resources/application.yml
 M catalog-service/src/test/java/com/dpp/catalog/InventoryServiceIntegrationTest.java
 M frontend/src/App.jsx
 M frontend/src/index.css
 M frontend/vite.config.js
 M infra/scripts/smoke-p2.ps1
 M infra/scripts/start-local-stack.ps1
 M notification-service/src/main/java/com/dpp/notification/service/NotificationRenderer.java
 M notification-service/src/main/java/com/dpp/notification/service/NotificationService.java
 M notification-service/src/test/java/com/dpp/notification/NotificationServiceIntegrationTest.java
 M order-service/pom.xml
 M order-service/src/main/java/com/dpp/order/domain/Order.java
 M order-service/src/main/java/com/dpp/order/domain/OrderStatus.java
 M order-service/src/main/java/com/dpp/order/messaging/OrderEventPublisher.java
 M order-service/src/main/java/com/dpp/order/messaging/PaymentEventListener.java
 M order-service/src/main/java/com/dpp/order/service/OrderSagaService.java
 M order-service/src/main/resources/application.yml
 M order-service/src/test/java/com/dpp/order/OrderServiceIntegrationTest.java
 M payment-service/pom.xml
 M payment-service/src/main/java/com/dpp/payment/config/PaymentProperties.java
 M payment-service/src/main/java/com/dpp/payment/config/ProviderConfiguration.java
 M payment-service/src/main/java/com/dpp/payment/domain/PaymentAttempt.java
 M payment-service/src/main/java/com/dpp/payment/domain/PaymentStatus.java
 M payment-service/src/main/java/com/dpp/payment/domain/Refund.java
 M payment-service/src/main/java/com/dpp/payment/domain/RefundStatus.java
 M payment-service/src/main/java/com/dpp/payment/messaging/OrderEventListener.java
 M payment-service/src/main/java/com/dpp/payment/messaging/PaymentEventPublisher.java
 M payment-service/src/main/java/com/dpp/payment/provider/PaymentProvider.java
 M payment-service/src/main/java/com/dpp/payment/provider/ProviderRefundStatus.java
 M payment-service/src/main/java/com/dpp/payment/provider/SimulatedPaymentProvider.java
 M payment-service/src/main/java/com/dpp/payment/provider/StripePaymentProvider.java
 M payment-service/src/main/java/com/dpp/payment/repository/PaymentAttemptRepository.java
 M payment-service/src/main/java/com/dpp/payment/repository/RefundRepository.java
 M payment-service/src/main/java/com/dpp/payment/service/PaymentReconciliationJob.java
 M payment-service/src/main/java/com/dpp/payment/service/PaymentService.java
 M payment-service/src/main/resources/application.yml
 M payment-service/src/test/java/com/dpp/payment/PaymentApiSecurityIntegrationTest.java
 M payment-service/src/test/java/com/dpp/payment/PaymentServiceIntegrationTest.java
 M platform-common/src/main/java/com/dpp/common/event/EventTypes.java
 M platform-messaging/src/main/java/com/dpp/messaging/OutboxRelay.java
?? .editorconfig
?? .env.example
?? .gitignore
?? .mvn/
?? .postman/
?? AGENTS.md
?? PRD.md
?? catalog-service/src/main/java/com/dpp/catalog/service/ReservationExpiryJob.java
?? catalog-service/src/main/resources/db/migration/V1_1__reservation_expiry_index.sql
?? docs/
?? frontend/src/api.js
?? frontend/src/components/
?? infra/scripts/smoke-p3.ps1
?? mvnw
?? order-service/src/main/resources/db/migration/V1_1__order_history_indexes.sql
?? order-service/src/test/java/com/dpp/order/OutboxRelayIntegrationTest.java
?? payment-service/src/main/java/com/dpp/payment/domain/PaymentTransition.java
?? payment-service/src/main/java/com/dpp/payment/provider/ResilientPaymentProvider.java
?? payment-service/src/main/java/com/dpp/payment/repository/PaymentTransitionRepository.java
?? payment-service/src/main/resources/db/migration/V1_1__align_payment_status_and_audit.sql
?? payment-service/src/test/java/com/dpp/payment/PaymentRefundPendingIntegrationTest.java
?? payment-service/src/test/java/com/dpp/payment/provider/
?? postman/
Recent commits:
d42702b Added Order Service Code Files
69a888c Added platform related workload source files
e4255ea Added source code for notification service
1c489f4 Initial Frontend Setup
5bcefae Adding dependency file
``

## Stage 1 limitations

This is a deterministic inventory. It does **not** semantically analyze source code, prove that features work, determine project completion, or run tests. Stage 2 will add local Ollama analysis.