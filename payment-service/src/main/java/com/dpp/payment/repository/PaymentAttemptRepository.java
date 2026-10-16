package com.dpp.payment.repository;

import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentAttempt> findByProviderPaymentId(String providerPaymentId);

    Optional<PaymentAttempt> findFirstByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    @Query("select p.id from PaymentAttempt p where p.status = :status and p.createdAt < :cutoff order by p.createdAt")
    List<UUID> findIdsByStatusAndCreatedAtBefore(@Param("status") PaymentStatus status,
                                                 @Param("cutoff") Instant cutoff);
}
