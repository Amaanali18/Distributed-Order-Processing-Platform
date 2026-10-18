package com.dpp.notification.service;

import com.dpp.common.event.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class NotificationRenderer {

    public record Rendered(String subject, String body) {
    }

    public Rendered render(String eventType, UUID orderId, JsonNode payload) {
        return switch (eventType) {
            case EventTypes.ORDER_CREATED -> new Rendered(
                    "Order received",
                    "We received order " + orderId + " for " + money(payload)
                            + ". We will confirm it once your payment succeeds.");
            case EventTypes.ORDER_CONFIRMED -> new Rendered(
                    "Order confirmed",
                    "Order " + orderId + " is confirmed and is being prepared.");
            case EventTypes.ORDER_CANCELLED -> new Rendered(
                    "Order cancelled",
                    "Order " + orderId + " was cancelled ("
                            + text(payload, "reason", "no reason given") + ").");
            case EventTypes.PAYMENT_SUCCEEDED -> new Rendered(
                    "Payment received",
                    "We received your payment of " + money(payload) + " for order " + orderId + ".");
            case EventTypes.PAYMENT_FAILED -> new Rendered(
                    "Payment failed",
                    "Your payment for order " + orderId + " failed ("
                            + text(payload, "reason", "unknown reason") + ").");
            case EventTypes.REFUND_COMPLETED -> new Rendered(
                    "Refund processed",
                    "A refund of " + money(payload) + " for order " + orderId + " has been processed.");
            default -> new Rendered(
                    "Update for order " + orderId,
                    "Order " + orderId + " changed state: " + eventType + ".");
        };
    }

    private String money(JsonNode payload) {
        JsonNode amountNode = payload.hasNonNull("totalAmount")
                ? payload.get("totalAmount")
                : payload.path("amount");
        String currency = payload.path("currency").asText("");
        if (amountNode == null || !amountNode.isNumber()) {
            return currency.isBlank() ? "the charged amount" : "the charged amount " + currency;
        }
        BigDecimal amount = amountNode.decimalValue();
        return currency.isBlank() ? amount.toPlainString() : amount.toPlainString() + " " + currency;
    }

    private String text(JsonNode payload, String field, String fallback) {
        String value = payload.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
}
