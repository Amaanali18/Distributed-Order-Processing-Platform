package com.dpp.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer", columnList = "customer_id"),
        @Index(name = "idx_orders_status", columnList = "status")})
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    private Order(UUID id, UUID customerId, String customerEmail, BigDecimal totalAmount, String currency,
                  List<OrderItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.items = new ArrayList<>(items);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Order pending(UUID id, UUID customerId, String customerEmail, BigDecimal totalAmount,
                                String currency, List<OrderItem> items) {
        return new Order(id, customerId, customerEmail, totalAmount, currency, items);
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void refund() {
        this.status = OrderStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Embeddable
    public static class OrderItem {

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(name = "sku", nullable = false, length = 64)
        private String sku;

        @Column(name = "name", nullable = false, length = 160)
        private String name;

        @Column(name = "quantity", nullable = false)
        private int quantity;

        @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal unitPrice;

        protected OrderItem() {
        }

        public OrderItem(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {
            this.productId = productId;
            this.sku = sku;
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public UUID getProductId() {
            return productId;
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }
}
