package com.dpp.payment.web.dto;

import com.dpp.payment.provider.PaymentOutcome;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(@NotNull PaymentOutcome outcome) {
}
