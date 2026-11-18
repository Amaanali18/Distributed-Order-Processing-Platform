package com.dpp.payment;

import com.dpp.payment.client.OrderClient;
import com.dpp.payment.client.dto.OrderView;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.domain.RefundStatus;
import com.dpp.payment.provider.ConfirmPaymentCommand;
import com.dpp.payment.provider.CreatePaymentCommand;
import com.dpp.payment.provider.CreateRefundCommand;
import com.dpp.payment.provider.PaymentOutcome;
import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.provider.ProviderRefund;
import com.dpp.payment.provider.ProviderRefundStatus;
import com.dpp.payment.repository.PaymentAttemptRepository;
import com.dpp.payment.repository.RefundRepository;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.ConfirmPaymentRequest;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000"
})
class PaymentRefundPendingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    PaymentService payments;

    @Autowired
    PaymentAttemptRepository attempts;

    @Autowired
    RefundRepository refunds;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PaymentProvider provider;

    @MockitoBean
    OrderClient orderClient;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void stubOrder() {
        when(orderClient.getOrder(any())).thenAnswer(inv -> new OrderView(
                inv.getArgument(0), customerId, "PENDING", new BigDecimal("19.98"), "USD"));
    }

    private int outboxCount(UUID orderId, String eventType) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_messages WHERE message_key = ? AND topic = ? AND payload LIKE ?",
                Integer.class, orderId.toString(), "payments.v1", "%" + eventType + "%");
        return count == null ? 0 : count;
    }

    private PaymentResponse createAndSucceed(UUID orderId) {
        when(provider.name()).thenReturn("simulated");
        when(provider.createPayment(any(CreatePaymentCommand.class)))
                .thenAnswer(inv -> ProviderPayment.pending("sim_pi_" + orderId));
        when(provider.confirmPayment(any(ConfirmPaymentCommand.class)))
                .thenAnswer(inv -> ProviderPayment.succeeded(
                        ((ConfirmPaymentCommand) inv.getArgument(0)).providerPaymentId()));

        PaymentResponse payment = payments.create(
                new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "usd"),
                customerId, "key-" + UUID.randomUUID());
        payments.confirm(payment.id(), customerId, false,
                new ConfirmPaymentRequest(PaymentOutcome.SUCCEEDED), "corr-confirm");
        return payment;
    }

    @Test
    void pendingRefundIsRecordedThenCompletedOnReconciliation() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createAndSucceed(orderId);

        when(provider.refund(any(CreateRefundCommand.class)))
                .thenAnswer(inv -> new ProviderRefund("sim_re_" + orderId, ProviderRefundStatus.PENDING, null));

        RefundResponse refund = payments.refund(payment.id(), new RefundRequest(null, "customer_request"), "corr");

        assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(outboxCount(orderId, "RefundPending")).isEqualTo(1);

        when(provider.findRefund("sim_re_" + orderId))
                .thenReturn(Optional.of(new ProviderRefund("sim_re_" + orderId, ProviderRefundStatus.SUCCEEDED, null)));

        payments.reconcileRefunds();

        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunds.findById(refund.id()).orElseThrow().getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(outboxCount(orderId, "RefundCompleted")).isEqualTo(1);
    }

    @Test
    void failedRefundRevertsPaymentToSuccess() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse payment = createAndSucceed(orderId);

        when(provider.refund(any(CreateRefundCommand.class)))
                .thenAnswer(inv -> new ProviderRefund("sim_re_" + orderId, ProviderRefundStatus.PENDING, null));
        RefundResponse refund = payments.refund(payment.id(), new RefundRequest(null, "customer_request"), "corr");

        when(provider.findRefund("sim_re_" + orderId))
                .thenReturn(Optional.of(new ProviderRefund("sim_re_" + orderId, ProviderRefundStatus.FAILED, "declined")));

        payments.reconcileRefunds();

        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(refunds.findById(refund.id()).orElseThrow().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(outboxCount(orderId, "RefundCompleted")).isZero();
    }
}
