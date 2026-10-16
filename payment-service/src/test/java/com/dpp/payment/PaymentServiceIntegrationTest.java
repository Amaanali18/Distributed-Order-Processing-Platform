package com.dpp.payment;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.domain.RefundStatus;
import com.dpp.payment.provider.PaymentOutcome;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.repository.PaymentAttemptRepository;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.ConfirmPaymentRequest;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000"
})
class PaymentServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    PaymentService payments;

    @Autowired
    PaymentAttemptRepository attempts;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID customerId = UUID.randomUUID();

    private String newKey() {
        return "key-" + UUID.randomUUID();
    }

    private PaymentResponse createPayment(UUID orderId) {
        return payments.create(new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "usd"),
                customerId, newKey());
    }

    private int outboxCount(UUID aggregateKey, String eventType) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_messages WHERE message_key = ? AND topic = ? AND payload LIKE ?",
                Integer.class, aggregateKey.toString(), "payments.v1", "%" + eventType + "%");
        return count == null ? 0 : count;
    }

    @Test
    void createPersistsPendingPayment() {
        UUID orderId = UUID.randomUUID();

        PaymentResponse payment = createPayment(orderId);

        assertThat(payment.id()).isNotNull();
        assertThat(payment.orderId()).isEqualTo(orderId);
        assertThat(payment.customerId()).isEqualTo(customerId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.provider()).isEqualTo("simulated");
        assertThat(payment.providerPaymentId()).startsWith("sim_pi_");
        assertThat(payment.currency()).isEqualTo("USD");
        assertThat(payment.amount()).isEqualByComparingTo("19.98");
    }

    @Test
    void createIsIdempotentForSameKeyAndPayload() {
        UUID orderId = UUID.randomUUID();
        String key = newKey();
        CreatePaymentRequest request = new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "USD");

        PaymentResponse first = payments.create(request, customerId, key);
        PaymentResponse second = payments.create(request, customerId, key);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.providerPaymentId()).isEqualTo(first.providerPaymentId());
        Integer stored = jdbc.queryForObject(
                "SELECT count(*) FROM payment_attempts WHERE order_id = ?", Integer.class, orderId);
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void createRejectsKeyReuseWithDifferentPayload() {
        UUID orderId = UUID.randomUUID();
        String key = newKey();
        payments.create(new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "USD"), customerId, key);

        assertThatThrownBy(() -> payments.create(
                new CreatePaymentRequest(orderId, new BigDecimal("5.00"), "USD"), customerId, key))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSE));
    }

    @Test
    void createRequiresIdempotencyKey() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> payments.create(
                new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "USD"), customerId, "  "))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void confirmSuccessPublishesPaymentSucceeded() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createPayment(orderId);

        PaymentResponse confirmed = payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.SUCCEEDED), "corr-confirm");

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outboxCount(orderId, "PaymentSucceeded")).isEqualTo(1);
    }

    @Test
    void confirmFailurePublishesPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createPayment(orderId);

        PaymentResponse failed = payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.FAILED), "corr-confirm");

        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("card_declined");
        assertThat(outboxCount(orderId, "PaymentFailed")).isEqualTo(1);
    }

    @Test
    void refundSucceededPaymentPublishesRefundCompleted() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createPayment(orderId);
        payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.SUCCEEDED), "corr-confirm");

        RefundResponse refund = payments.refund(payment.id(), new RefundRequest(null, "customer request"),
                "corr-refund");

        assertThat(refund.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(refund.providerRefundId()).startsWith("sim_re_");
        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outboxCount(orderId, "RefundCompleted")).isEqualTo(1);
    }

    @Test
    void refundPendingPaymentIsRejected() {
        PaymentResponse payment = createPayment(UUID.randomUUID());

        assertThatThrownBy(() -> payments.refund(payment.id(), new RefundRequest(null, null), "corr"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void refundAbovePaymentAmountIsRejected() {
        PaymentResponse payment = createPayment(UUID.randomUUID());
        payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.SUCCEEDED), "corr");

        assertThatThrownBy(() -> payments.refund(payment.id(), new RefundRequest(new BigDecimal("100.00"), null), "corr"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void providerEventSettlesPaymentLikeAWebhook() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createPayment(orderId);

        payments.handleProviderEvent(ProviderPayment.succeeded(payment.providerPaymentId()), "corr-webhook");

        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outboxCount(orderId, "PaymentSucceeded")).isEqualTo(1);
    }

    @Test
    void cancellingOrderCompensatesSucceededPayment() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createPayment(orderId);
        payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.SUCCEEDED), "corr");

        payments.onOrderCancelled(UUID.randomUUID(), orderId, "corr-cancel");

        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outboxCount(orderId, "RefundCompleted")).isEqualTo(1);
    }

    @Test
    void getRejectsOtherCustomers() {
        PaymentResponse payment = createPayment(UUID.randomUUID());

        assertThatThrownBy(() -> payments.get(payment.id(), UUID.randomUUID(), false))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
