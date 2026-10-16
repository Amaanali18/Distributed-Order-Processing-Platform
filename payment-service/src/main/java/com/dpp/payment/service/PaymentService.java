package com.dpp.payment.service;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.messaging.ProcessedEventStore;
import com.dpp.common.event.EventTypes;
import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.domain.Refund;
import com.dpp.payment.domain.RefundStatus;
import com.dpp.payment.messaging.PaymentEventPublisher;
import com.dpp.payment.provider.ConfirmPaymentCommand;
import com.dpp.payment.provider.CreatePaymentCommand;
import com.dpp.payment.provider.CreateRefundCommand;
import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.provider.ProviderRefund;
import com.dpp.payment.provider.ProviderRefundStatus;
import com.dpp.payment.repository.PaymentAttemptRepository;
import com.dpp.payment.repository.RefundRepository;
import com.dpp.payment.web.dto.ConfirmPaymentRequest;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentAttemptRepository attempts;
    private final RefundRepository refunds;
    private final PaymentProvider provider;
    private final PaymentEventPublisher publisher;
    private final ProcessedEventStore processedEvents;

    public PaymentService(PaymentAttemptRepository attempts,
                          RefundRepository refunds,
                          PaymentProvider provider,
                          PaymentEventPublisher publisher,
                          ProcessedEventStore processedEvents) {
        this.attempts = attempts;
        this.refunds = refunds;
        this.provider = provider;
        this.publisher = publisher;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, UUID customerId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key header is required");
        }
        String currency = request.currency().toUpperCase(Locale.ROOT);

        PaymentAttempt existing = attempts.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.matches(request.orderId(), request.amount(), currency)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSE,
                        "Idempotency key " + idempotencyKey + " was reused with a different payload");
            }
            return PaymentResponse.from(existing);
        }

        ProviderPayment providerPayment = provider.createPayment(new CreatePaymentCommand(
                idempotencyKey, request.orderId(), customerId, request.amount(), currency));

        PaymentAttempt attempt = PaymentAttempt.pending(UUID.randomUUID(), request.orderId(), customerId,
                request.amount(), currency, provider.name(), providerPayment.providerPaymentId(), idempotencyKey);
        attempts.save(attempt);
        log.info("Created payment attempt {} for order {} using provider {}",
                attempt.getId(), request.orderId(), provider.name());
        return PaymentResponse.from(attempt);
    }

    @Transactional
    public PaymentResponse confirm(UUID paymentId, UUID customerId, boolean admin,
                                   ConfirmPaymentRequest request, String correlationId) {
        PaymentAttempt attempt = require(paymentId);
        authorize(attempt, customerId, admin);
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            return PaymentResponse.from(attempt);
        }
        ProviderPayment result = provider.confirmPayment(
                new ConfirmPaymentCommand(attempt.getProviderPaymentId(), request.outcome()));
        settle(attempt, result, correlationId);
        return PaymentResponse.from(attempt);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, UUID customerId, boolean admin) {
        PaymentAttempt attempt = require(paymentId);
        authorize(attempt, customerId, admin);
        return PaymentResponse.from(attempt);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrder(UUID orderId) {
        return attempts.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
                .or(() -> attempts.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PENDING))
                .or(() -> attempts.findFirstByOrderIdAndStatus(orderId, PaymentStatus.FAILED))
                .map(PaymentResponse::from)
                .orElseThrow(() -> ApiException.notFound("No payment found for order " + orderId));
    }

    @Transactional
    public RefundResponse refund(UUID paymentId, RefundRequest request, String correlationId) {
        PaymentAttempt attempt = require(paymentId);
        if (attempt.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only succeeded payments can be refunded, current status is " + attempt.getStatus());
        }
        BigDecimal amount = request.amount() == null ? attempt.getAmount() : request.amount();
        if (amount.signum() <= 0 || amount.compareTo(attempt.getAmount()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Refund amount must be between 0 and " + attempt.getAmount());
        }
        String reason = request.reason() == null || request.reason().isBlank()
                ? "requested_by_customer" : request.reason();
        Refund refund = compensate(attempt, amount, reason, correlationId);
        return RefundResponse.from(refund);
    }

    @Transactional
    public void handleProviderEvent(ProviderPayment payment, String correlationId) {
        PaymentAttempt attempt = attempts.findByProviderPaymentId(payment.providerPaymentId())
                .orElseThrow(() -> ApiException.notFound("Unknown provider payment " + payment.providerPaymentId()));
        settle(attempt, payment, correlationId);
    }

    @Transactional
    public void reconcile(UUID paymentId) {
        PaymentAttempt attempt = attempts.findById(paymentId).orElse(null);
        if (attempt == null || attempt.getStatus() != PaymentStatus.PENDING
                || attempt.getProviderPaymentId() == null) {
            return;
        }
        provider.findPayment(attempt.getProviderPaymentId())
                .ifPresent(result -> settle(attempt, result, attempt.getId().toString()));
    }

    @Transactional
    public void onOrderCancelled(UUID eventId, UUID orderId, String correlationId) {
        if (!processedEvents.firstTime(eventId, EventTypes.ORDER_CANCELLED)) {
            log.debug("Duplicate OrderCancelled event {} ignored", eventId);
            return;
        }
        attempts.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED).ifPresent(attempt -> {
            log.warn("Compensating payment {} because order {} was cancelled", attempt.getId(), orderId);
            compensate(attempt, attempt.getAmount(), "order_cancelled", correlationId);
        });
    }

    private void settle(PaymentAttempt attempt, ProviderPayment result, String correlationId) {
        switch (result.status()) {
            case SUCCEEDED -> {
                if (attempt.getStatus() == PaymentStatus.SUCCEEDED) {
                    return;
                }
                attempt.succeed();
                attempts.save(attempt);
                publisher.paymentSucceeded(attempt, correlationId);
                log.info("Payment {} succeeded for order {}", attempt.getId(), attempt.getOrderId());
            }
            case FAILED -> {
                if (attempt.getStatus() == PaymentStatus.FAILED) {
                    return;
                }
                attempt.fail(result.failureCode(), result.failureMessage());
                attempts.save(attempt);
                publisher.paymentFailed(attempt, correlationId);
                log.info("Payment {} failed for order {}", attempt.getId(), attempt.getOrderId());
            }
            case PENDING -> log.debug("Payment {} is still pending at the provider", attempt.getId());
        }
    }

    private Refund compensate(PaymentAttempt attempt, BigDecimal amount, String reason, String correlationId) {
        ProviderRefund providerRefund = provider.refund(new CreateRefundCommand(
                "refund-" + attempt.getId(), attempt.getProviderPaymentId(), amount,
                attempt.getCurrency(), reason));

        RefundStatus status = providerRefund.status() == ProviderRefundStatus.SUCCEEDED
                ? RefundStatus.SUCCEEDED : RefundStatus.FAILED;
        Refund refund = Refund.record(UUID.randomUUID(), attempt.getId(), attempt.getOrderId(), amount,
                attempt.getCurrency(), status, providerRefund.providerRefundId(), reason);
        refunds.save(refund);

        if (status == RefundStatus.SUCCEEDED) {
            attempt.markRefunded();
            attempts.save(attempt);
            publisher.refundCompleted(attempt, refund, correlationId);
            log.info("Refund {} completed for payment {}", refund.getId(), attempt.getId());
        } else {
            log.warn("Refund {} failed for payment {}", refund.getId(), attempt.getId());
        }
        return refund;
    }

    private PaymentAttempt require(UUID paymentId) {
        return attempts.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found: " + paymentId));
    }

    private void authorize(PaymentAttempt attempt, UUID customerId, boolean admin) {
        if (!admin && !attempt.getCustomerId().equals(customerId)) {
            throw ApiException.forbidden("Payment does not belong to the current user");
        }
    }
}
