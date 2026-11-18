package com.dpp.payment.repository;

import com.dpp.payment.domain.PaymentTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentTransitionRepository extends JpaRepository<PaymentTransition, UUID> {

    List<PaymentTransition> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
