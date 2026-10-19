package com.dpp.order.web.dto;

import com.dpp.order.domain.Order;
import com.dpp.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getSku(), item.getName(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
