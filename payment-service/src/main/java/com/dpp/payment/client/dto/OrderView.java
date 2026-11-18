package com.dpp.payment.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal projection of an order used to verify that a payment is legitimate.
 */
public record OrderView(UUID id, UUID customerId, String status, BigDecimal totalAmount, String currency) {
}
