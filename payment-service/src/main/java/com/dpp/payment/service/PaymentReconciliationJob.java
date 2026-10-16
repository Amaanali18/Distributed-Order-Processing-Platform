package com.dpp.payment.service;

import com.dpp.payment.config.PaymentProperties;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.repository.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentAttemptRepository attempts;
    private final PaymentService paymentService;
    private final long staleAfterMs;

    public PaymentReconciliationJob(PaymentAttemptRepository attempts,
                                    PaymentService paymentService,
                                    PaymentProperties properties) {
        this.attempts = attempts;
        this.paymentService = paymentService;
        this.staleAfterMs = properties.reconciliation() == null
                ? 120_000L : properties.reconciliation().staleAfterMsOrDefault();
    }

    @Scheduled(fixedDelayString = "${dpp.payment.reconciliation.interval-ms:60000}")
    public void reconcileStalePayments() {
        Instant cutoff = Instant.now().minus(Duration.ofMillis(staleAfterMs));
        List<UUID> stale = attempts.findIdsByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Reconciling {} stale payment attempt(s)", stale.size());
        for (UUID paymentId : stale) {
            try {
                paymentService.reconcile(paymentId);
            } catch (Exception e) {
                log.warn("Reconciliation failed for payment {}: {}", paymentId, e.getMessage());
            }
        }
    }
}
