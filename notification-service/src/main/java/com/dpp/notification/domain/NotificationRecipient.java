package com.dpp.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationRecipient() {
    }

    private NotificationRecipient(UUID orderId, UUID customerId, String email) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public static NotificationRecipient of(UUID orderId, UUID customerId, String email) {
        return new NotificationRecipient(orderId, customerId, email);
    }

    public void update(UUID customerId, String email) {
        this.customerId = customerId;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
