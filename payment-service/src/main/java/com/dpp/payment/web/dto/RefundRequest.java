package com.dpp.payment.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 256) String reason) {
}
