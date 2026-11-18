package com.dpp.payment.service;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.messaging.ProcessedEventStore;
import com.dpp.payment.client.OrderClient;
import com.dpp.payment.client.dto.OrderView;
import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.domain.PaymentTransition;
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
import com.dpp.payment.repository.PaymentTransitionRepository;
import com.dpp.payment.repository.RefundRepository;
import com.dpp.payment.web.dto.ConfirmPaymentRequest;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentAttemptRepository attempts;
    private final RefundRepository refunds;
    private final PaymentTransitionRepository transitions;
    private final PaymentProvider provider;
    private final PaymentEventPublisher publisher;
    private final ProcessedEventStore processedEvents;
    private final OrderClient orderClient;
    private final PaymentAttemptInserter inserter;

    public PaymentService(PaymentAttemptRepository attempts,
                          RefundRepository refunds,
                          PaymentTransitionRepository transitions,
                          PaymentProvider provider,
                          PaymentEventPublisher publisher,
                          ProcessedEventStore processedEvents,
                          OrderClient orderClient,
                          PaymentAttemptInserter inserter) {
        this.attempts = attempts;
        this.refunds = refunds;
        this.transitions = transitions;
        this.provider = provider;
        this.publisher = publisher;
        this.processedEvents = processedEvents;
        this.orderClient = orderClient;
        this.inserter = inserter;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, UUID customerId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key header is required");
        }
        String currency = request.currency().toUpperCase(Locale.ROOT);

        PaymentAttempt existing = attempts.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            verifyIdempotentReplay(existing, request, currency, customerId, idempotencyKey);
            return PaymentResponse.from(existing);
        }

        OrderView order = loadOrder(request.orderId());
        if (!order.customerId().equals(customerId)) {
            throw ApiException.forbidden("Order does not belong to the current user");
        }
        if (!"PENDING".equals(order.status())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only pending orders can be paid, current status is " + order.status());
        }
        if (!currency.equalsIgnoreCase(order.currency())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Payment currency must match the order currency " + order.currency());
        }
        if (request.amount().compareTo(order.totalAmount()) != 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Payment amount must match the server-side order total " + order.totalAmount());
        }

        // The provider idempotency key makes a repeated provider call harmless.
        // The unique database constraint on idempotency_key is the durable guard:
        // a concurrent loser re-reads the winner's attempt instead of returning 500.
        ProviderPayment providerPayment = provider.createPayment(new CreatePaymentCommand(
                idempotencyKey, request.orderId(), customerId, request.amount(), currency));

        PaymentAttempt attempt = PaymentAttempt.pending(UUID.randomUUID(), request.orderId(), customerId,
                request.amount(), currency, provider.name(), providerPayment.providerPaymentId(), idempotencyKey);
        try {
            inserter.insert(attempt);
        } catch (DataIntegrityViolationException e) {
            PaymentAttempt winner = attempts.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                            "Concurrent payment creation for key " + idempotencyKey + ", retry the request"));
            verifyIdempotentReplay(winner, request, currency, customerId, idempotencyKey);
            log.info("Concurrent create for idempotency key {} returned existing attempt {}",
                    idempotencyKey, winner.getId());
            return PaymentResponse.from(winner);
        }
        recordTransition(attempt, null, "created");
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
    public Page<PaymentResponse> listMine(UUID customerId, Pageable pageable) {
        return attempts.findByCustomerId(customerId, pageable).map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listAll(Pageable pageable) {
        return attempts.findAll(pageable).map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrder(UUID orderId, UUID customerId, boolean admin) {
        List<PaymentAttempt> forOrder = attempts.findByOrderId(orderId);
        if (forOrder.isEmpty()) {
            throw ApiException.notFound("No payment found for order " + orderId);
        }
        if (!admin && forOrder.stream().noneMatch(a -> a.getCustomerId().equals(customerId))) {
            throw ApiException.forbidden("Payments for this order do not belong to the current user");
        }
        for (PaymentStatus status : new PaymentStatus[]{PaymentStatus.SUCCESS, PaymentStatus.REFUND_PENDING,
                PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.REFUNDED}) {
            for (PaymentAttempt attempt : forOrder) {
                if (attempt.getStatus() == status) {
                    return PaymentResponse.from(attempt);
                }
            }
        }
        return PaymentResponse.from(forOrder.get(0));
    }

    @Transactional
    public RefundResponse refund(UUID paymentId, RefundRequest request, String correlationId) {
        PaymentAttempt attempt = require(paymentId);
        if (attempt.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only successful payments can be refunded, current status is " + attempt.getStatus());
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
    public void reconcileRefunds() {
        for (UUID refundId : refunds.findIdsByStatus(RefundStatus.PENDING)) {
            Refund refund = refunds.findById(refundId).orElse(null);
            if (refund == null || refund.getProviderRefundId() == null) {
                continue;
            }
            provider.findRefund(refund.getProviderRefundId())
                    .ifPresent(result -> applyRefundResult(refund, result, "refund_reconciled"));
        }
    }

    @Transactional
    public void onOrderTerminated(UUID eventId, String eventType, UUID orderId, String correlationId) {
        if (!processedEvents.firstTime(eventId, eventType)) {
            log.debug("Duplicate {} event {} ignored", eventType, eventId);
            return;
        }
        for (PaymentAttempt attempt : attempts.findByOrderId(orderId)) {
            switch (attempt.getStatus()) {
                case SUCCESS -> {
                    log.warn("Compensating payment {} because order {} terminated via {}",
                            attempt.getId(), orderId, eventType);
                    compensate(attempt, attempt.getAmount(), "order_terminated", correlationId);
                }
                case PENDING -> {
                    attempt.requestCancellation();
                    attempts.save(attempt);
                    log.info("Flagged payment {} for refund if it settles after order {} terminated",
                            attempt.getId(), orderId);
                }
                default -> log.debug("Payment {} in status {} needs no termination action",
                        attempt.getId(), attempt.getStatus());
            }
        }
    }

    private void settle(PaymentAttempt attempt, ProviderPayment result, String correlationId) {
        PaymentStatus before = attempt.getStatus();
        switch (result.status()) {
            case SUCCEEDED -> {
                if (before == PaymentStatus.SUCCESS) {
                    return;
                }
                if (before != PaymentStatus.PENDING) {
                    log.warn("Payment {} settled as success from status {}", attempt.getId(), before);
                    return;
                }
                attempt.succeed();
                attempts.save(attempt);
                recordTransition(attempt, before, "provider_success");
                if (attempt.isCancellationRequested()) {
                    log.warn("Payment {} succeeded after order termination, refunding", attempt.getId());
                    compensate(attempt, attempt.getAmount(), "late_success_after_termination", correlationId);
                } else {
                    publisher.paymentSucceeded(attempt, correlationId);
                    log.info("Payment {} succeeded for order {}", attempt.getId(), attempt.getOrderId());
                }
            }
            case FAILED -> {
                if (before == PaymentStatus.FAILED) {
                    return;
                }
                if (before != PaymentStatus.PENDING) {
                    log.warn("Payment {} settled as failed from status {}", attempt.getId(), before);
                    return;
                }
                attempt.fail(result.failureCode(), result.failureMessage());
                attempts.save(attempt);
                recordTransition(attempt, before, "provider_failure");
                publisher.paymentFailed(attempt, correlationId);
                log.info("Payment {} failed for order {}", attempt.getId(), attempt.getOrderId());
            }
            case PENDING -> log.debug("Payment {} is still pending at the provider", attempt.getId());
        }
    }

    private Refund compensate(PaymentAttempt attempt, BigDecimal amount, String reason, String correlationId) {
        PaymentStatus before = attempt.getStatus();
        if (before != PaymentStatus.SUCCESS && before != PaymentStatus.REFUND_PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Payment " + attempt.getId() + " cannot be refunded from status " + before);
        }

        ProviderRefund providerRefund = provider.refund(new CreateRefundCommand(
                "refund-" + attempt.getId() + "-" + UUID.randomUUID(), attempt.getProviderPaymentId(), amount,
                attempt.getCurrency(), reason));

        RefundStatus status = switch (providerRefund.status()) {
            case SUCCEEDED -> RefundStatus.SUCCEEDED;
            case PENDING -> RefundStatus.PENDING;
            case FAILED -> RefundStatus.FAILED;
        };
        Refund refund = Refund.record(UUID.randomUUID(), attempt.getId(), attempt.getOrderId(), amount,
                attempt.getCurrency(), status, providerRefund.providerRefundId(), reason);
        refunds.save(refund);
        applyRefundStatus(attempt, refund, status, before, reason, correlationId);
        return refund;
    }

    private void applyRefundResult(Refund refund, ProviderRefund result, String reason) {
        if (!refund.isPending()) {
            return;
        }
        PaymentAttempt attempt = attempts.findById(refund.getPaymentId()).orElse(null);
        if (attempt == null) {
            log.warn("Refund {} references unknown payment {}", refund.getId(), refund.getPaymentId());
            return;
        }
        RefundStatus status = switch (result.status()) {
            case SUCCEEDED -> RefundStatus.SUCCEEDED;
            case PENDING -> RefundStatus.PENDING;
            case FAILED -> RefundStatus.FAILED;
        };
        applyRefundStatus(attempt, refund, status, attempt.getStatus(), reason, refund.getId().toString());
    }

    private void applyRefundStatus(PaymentAttempt attempt, Refund refund, RefundStatus status,
                                   PaymentStatus before, String reason, String correlationId) {
        switch (status) {
            case SUCCEEDED -> {
                refund.markSucceeded();
                refunds.save(refund);
                attempt.markRefunded();
                attempts.save(attempt);
                recordTransition(attempt, before, reason);
                publisher.refundCompleted(attempt, refund, correlationId);
                log.info("Refund {} completed for payment {}", refund.getId(), attempt.getId());
            }
            case PENDING -> {
                refund.markPending();
                refunds.save(refund);
                attempt.markRefundPending();
                attempts.save(attempt);
                recordTransition(attempt, before, reason);
                publisher.refundPending(attempt, refund, correlationId);
                log.info("Refund {} pending for payment {}", refund.getId(), attempt.getId());
            }
            case FAILED -> {
                refund.markFailed();
                refunds.save(refund);
                if (attempt.getStatus() == PaymentStatus.REFUND_PENDING) {
                    attempt.revertToSuccess();
                    attempts.save(attempt);
                    recordTransition(attempt, before, reason);
                }
                log.warn("Refund {} failed for payment {}", refund.getId(), attempt.getId());
            }
        }
    }

    private void recordTransition(PaymentAttempt attempt, PaymentStatus from, String reason) {
        if (from == attempt.getStatus()) {
            return;
        }
        transitions.save(PaymentTransition.of(attempt.getId(), from, attempt.getStatus(), reason));
    }

    private OrderView loadOrder(UUID orderId) {
        try {
            return orderClient.getOrder(orderId);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Order not found: " + orderId);
        } catch (FeignException e) {
            log.warn("Order lookup failed for payment on order {}: {}", orderId, e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Order service is unavailable");
        }
    }

    private void verifyIdempotentReplay(PaymentAttempt existing, CreatePaymentRequest request,
                                        String currency, UUID customerId, String idempotencyKey) {
        if (!existing.matches(request.orderId(), request.amount(), currency)
                || !existing.getCustomerId().equals(customerId)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSE,
                    "Idempotency key " + idempotencyKey + " was reused with a different payload");
        }
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
