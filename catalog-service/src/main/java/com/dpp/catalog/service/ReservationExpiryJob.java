package com.dpp.catalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final InventoryService inventory;

    public ReservationExpiryJob(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Scheduled(fixedDelayString = "${dpp.catalog.reservation-sweep-interval-ms:60000}")
    public void releaseExpiredReservations() {
        try {
            int released = inventory.releaseExpired();
            if (released > 0) {
                log.info("Released {} expired reservation(s)", released);
            }
        } catch (Exception e) {
            log.warn("Reservation expiry sweep failed: {}", e.getMessage());
        }
    }
}
