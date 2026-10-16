package com.dpp.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_status", columnList = "status"))
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    private Product(UUID id, String sku, String name, String description, BigDecimal price, String currency,
                    ProductStatus status, int totalStock, int reservedStock) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.status = status;
        this.totalStock = totalStock;
        this.reservedStock = reservedStock;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Product create(String sku, String name, String description, BigDecimal price, String currency,
                                 int totalStock) {
        return new Product(UUID.randomUUID(), sku, name, description, price, currency,
                ProductStatus.ACTIVE, totalStock, 0);
    }

    public void updateDetails(String name, String description, BigDecimal price, String currency, int totalStock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.totalStock = totalStock;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void addStock(int quantity) {
        this.totalStock += quantity;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public int getAvailableStock() {
        return totalStock - reservedStock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
