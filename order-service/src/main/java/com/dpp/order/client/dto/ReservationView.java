package com.dpp.order.client.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationView(
        UUID id,
        UUID orderId,
        String status,
        List<Item> items,
        Instant expiresAt) {

    public record Item(UUID productId, int quantity) {
    }
}
