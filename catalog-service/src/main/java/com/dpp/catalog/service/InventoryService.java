package com.dpp.catalog.service;

import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.domain.Reservation;
import com.dpp.catalog.domain.Reservation.ReservationItem;
import com.dpp.catalog.domain.ReservationStatus;
import com.dpp.catalog.repository.ProductRepository;
import com.dpp.catalog.repository.ReservationRepository;
import com.dpp.catalog.web.dto.ReservationRequest;
import com.dpp.catalog.web.dto.ReservationResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);

    private final ProductRepository products;
    private final ReservationRepository reservations;

    public InventoryService(ProductRepository products, ReservationRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        Reservation existing = reservations.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            return ReservationResponse.from(existing);
        }

        Instant now = Instant.now();
        List<ReservationItem> items = new ArrayList<>();
        for (ReservationRequest.Item item : request.items()) {
            int updated = products.reserveStock(item.productId(), item.quantity(), ProductStatus.ACTIVE, now);
            if (updated == 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                        "Insufficient stock for product " + item.productId());
            }
            items.add(new ReservationItem(item.productId(), item.quantity()));
        }

        Instant expiresAt = request.expiresAt() != null ? request.expiresAt() : now.plus(DEFAULT_RESERVATION_TTL);
        Reservation reservation = Reservation.held(request.orderId(), items, expiresAt);
        reservations.save(reservation);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse commit(UUID orderId) {
        Reservation reservation = reservations.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Reservation not found for order " + orderId));
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            return ReservationResponse.from(reservation);
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Reservation already released for order " + orderId);
        }

        Instant now = Instant.now();
        for (ReservationItem item : reservation.getItems()) {
            int updated = products.commitStock(item.getProductId(), item.getQuantity(), now);
            if (updated == 0) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Failed to commit reservation for product " + item.getProductId());
            }
        }
        reservation.commit();
        reservations.save(reservation);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse release(UUID orderId) {
        Reservation reservation = reservations.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Reservation not found for order " + orderId));
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return ReservationResponse.from(reservation);
        }
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Reservation already committed for order " + orderId);
        }

        Instant now = Instant.now();
        for (ReservationItem item : reservation.getItems()) {
            int updated = products.releaseStock(item.getProductId(), item.getQuantity(), now);
            if (updated == 0) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Failed to release reservation for product " + item.getProductId());
            }
        }
        reservation.release();
        reservations.save(reservation);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID orderId) {
        return reservations.findByOrderId(orderId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> ApiException.notFound("Reservation not found for order " + orderId));
    }
}
