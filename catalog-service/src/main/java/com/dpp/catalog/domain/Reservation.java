package com.dpp.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_items", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationItem> items = new ArrayList<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    private Reservation(UUID id, UUID orderId, List<ReservationItem> items, Instant expiresAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = ReservationStatus.HELD;
        this.items = new ArrayList<>(items);
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Reservation held(UUID orderId, List<ReservationItem> items, Instant expiresAt) {
        return new Reservation(UUID.randomUUID(), orderId, items, expiresAt);
    }

    public void commit() {
        this.status = ReservationStatus.COMMITTED;
        this.updatedAt = Instant.now();
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now) && status == ReservationStatus.HELD;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class ReservationItem {

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(name = "quantity", nullable = false)
        private int quantity;

        protected ReservationItem() {
        }

        public ReservationItem(UUID productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public UUID getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
