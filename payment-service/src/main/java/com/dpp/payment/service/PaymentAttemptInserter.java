package com.dpp.payment.service;

import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.repository.PaymentAttemptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a payment attempt in its own transaction so a unique-key collision
 * can be caught by the caller without marking the surrounding transaction as
 * rollback-only.
 */
@Component
public class PaymentAttemptInserter {

    private final PaymentAttemptRepository attempts;

    public PaymentAttemptInserter(PaymentAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(PaymentAttempt attempt) {
        attempts.saveAndFlush(attempt);
    }
}
