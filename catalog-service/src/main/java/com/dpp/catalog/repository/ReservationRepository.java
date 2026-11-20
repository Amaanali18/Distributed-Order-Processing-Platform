package com.dpp.catalog.repository;

import com.dpp.catalog.domain.Reservation;
import com.dpp.catalog.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<Reservation> findTop200ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(ReservationStatus status, Instant cutoff);
}
