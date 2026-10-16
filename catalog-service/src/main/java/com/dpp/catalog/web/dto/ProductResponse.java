package com.dpp.catalog.web.dto;

import com.dpp.catalog.domain.Product;
import com.dpp.catalog.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        ProductStatus status,
        int totalStock,
        int reservedStock,
        int availableStock,
        Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus(),
                product.getTotalStock(),
                product.getReservedStock(),
                product.getAvailableStock(),
                product.getCreatedAt());
    }
}
