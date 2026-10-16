package com.dpp.catalog.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID orderId,
        @NotEmpty @Valid List<Item> items,
        Instant expiresAt) {

    public record Item(@NotNull UUID productId, @Min(1) int quantity) {
    }
}
