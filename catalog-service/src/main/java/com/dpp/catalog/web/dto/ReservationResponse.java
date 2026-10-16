package com.dpp.catalog.web.dto;

import com.dpp.catalog.domain.Reservation;
import com.dpp.catalog.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        List<Item> items,
        Instant expiresAt) {

    public record Item(UUID productId, int quantity) {
    }

    public static ReservationResponse from(Reservation reservation) {
        List<Item> items = reservation.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getQuantity()))
                .toList();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                items,
                reservation.getExpiresAt());
    }
}
