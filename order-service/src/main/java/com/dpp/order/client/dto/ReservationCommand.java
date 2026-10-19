package com.dpp.order.client.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCommand(
        UUID orderId,
        List<Item> items,
        Instant expiresAt) {

    public record Item(UUID productId, int quantity) {
    }
}
