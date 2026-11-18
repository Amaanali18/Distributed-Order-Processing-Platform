package com.dpp.payment.repository;

import com.dpp.payment.domain.Refund;
import com.dpp.payment.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findFirstByPaymentIdAndStatus(UUID paymentId, RefundStatus status);

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    List<Refund> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Query("select r.id from Refund r where r.status = :status order by r.createdAt")
    List<UUID> findIdsByStatus(@Param("status") RefundStatus status);
}
