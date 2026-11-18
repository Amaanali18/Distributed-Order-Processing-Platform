package com.dpp.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refunds_payment", columnList = "payment_id")})
public class Refund {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "provider_refund_id", length = 128)
    private String providerRefundId;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Refund() {
    }

    private Refund(UUID id, UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                   RefundStatus status, String providerRefundId, String reason) {
        this.id = id;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.providerRefundId = providerRefundId;
        this.reason = reason;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Refund record(UUID id, UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                                RefundStatus status, String providerRefundId, String reason) {
        return new Refund(id, paymentId, orderId, amount, currency, status, providerRefundId, reason);
    }

    public void markPending() {
        this.status = RefundStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markSucceeded() {
        this.status = RefundStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = RefundStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return status == RefundStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
