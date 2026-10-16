package com.dpp.payment.repository;

import com.dpp.payment.domain.Refund;
import com.dpp.payment.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findFirstByPaymentIdAndStatus(UUID paymentId, RefundStatus status);
}
