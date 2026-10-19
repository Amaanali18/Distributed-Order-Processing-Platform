package com.dpp.order.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductView(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        String status) {
}
