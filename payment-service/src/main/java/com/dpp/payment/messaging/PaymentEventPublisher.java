package com.dpp.payment.messaging;

import com.dpp.common.event.EventEnvelope;
import com.dpp.common.event.EventTypes;
import com.dpp.common.event.Topics;
import com.dpp.messaging.OutboxWriter;
import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.domain.Refund;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentEventPublisher {

    private static final String PRODUCER = "payment-service";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxWriter outbox;

    public PaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void paymentSucceeded(PaymentAttempt attempt, String correlationId) {
        publish(EventTypes.PAYMENT_SUCCEEDED, attempt, correlationId, Map.of(
                "orderId", attempt.getOrderId(),
                "paymentId", attempt.getId(),
                "amount", attempt.getAmount(),
                "currency", attempt.getCurrency()));
    }

    public void paymentFailed(PaymentAttempt attempt, String correlationId) {
        publish(EventTypes.PAYMENT_FAILED, attempt, correlationId, Map.of(
                "orderId", attempt.getOrderId(),
                "paymentId", attempt.getId(),
                "reason", attempt.getFailureMessage() == null ? "payment failed" : attempt.getFailureMessage()));
    }

    public void refundCompleted(PaymentAttempt attempt, Refund refund, String correlationId) {
        publish(EventTypes.REFUND_COMPLETED, attempt, correlationId, Map.of(
                "orderId", attempt.getOrderId(),
                "paymentId", attempt.getId(),
                "refundId", refund.getId(),
                "amount", refund.getAmount(),
                "currency", refund.getCurrency()));
    }

    private void publish(String eventType, PaymentAttempt attempt, String correlationId, Map<String, Object> payload) {
        EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.of(eventType, SCHEMA_VERSION, attempt.getOrderId().toString(),
                        correlationId, PRODUCER, payload);
        outbox.enqueue(Topics.PAYMENTS, attempt.getOrderId().toString(), envelope);
    }
}
