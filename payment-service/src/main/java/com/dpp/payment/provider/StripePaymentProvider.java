package com.dpp.payment.provider;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.payment.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

public class StripePaymentProvider implements PaymentProvider {

    private static final int DEFAULT_CURRENCY_EXPONENT = 2;

    private final RestClient http;
    private final String webhookSecret;

    public StripePaymentProvider(PaymentProperties.Stripe settings) {
        this.webhookSecret = settings.webhookSecret();
        this.http = RestClient.builder()
                .baseUrl(settings.apiBaseOrDefault())
                .defaultHeader("Authorization", "Bearer " + (settings.apiKey() == null ? "" : settings.apiKey()))
                .build();
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ProviderPayment createPayment(CreatePaymentCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", minorUnits(command.amount()));
        form.add("currency", command.currency().toLowerCase(Locale.ROOT));
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("metadata[orderId]", command.orderId().toString());
        form.add("metadata[customerId]", command.customerId().toString());

        JsonNode response = post("/v1/payment_intents", form, command.idempotencyKey());
        return toProviderPayment(response);
    }

    @Override
    public ProviderPayment confirmPayment(ConfirmPaymentCommand command) {
        JsonNode response = get("/v1/payment_intents/" + command.providerPaymentId());
        return toProviderPayment(response);
    }

    @Override
    public Optional<ProviderPayment> findPayment(String providerPaymentId) {
        try {
            return Optional.of(toProviderPayment(get("/v1/payment_intents/" + providerPaymentId)));
        } catch (ApiException e) {
            if (e.errorCode() == ErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public ProviderRefund refund(CreateRefundCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", command.providerPaymentId());
        form.add("amount", minorUnits(command.amount()));
        if (command.reason() != null && !command.reason().isBlank()) {
            form.add("reason", mapRefundReason(command.reason()));
        }
        JsonNode response = post("/v1/refunds", form, command.idempotencyKey());
        String status = response.path("status").asText();
        if ("succeeded".equals(status) || "pending".equals(status)) {
            return new ProviderRefund(response.path("id").asText(), ProviderRefundStatus.SUCCEEDED, null);
        }
        return new ProviderRefund(response.path("id").asText(), ProviderRefundStatus.FAILED,
                response.path("failure_reason").asText(null));
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        return StripeWebhookSignature.verify(payload, signatureHeader, webhookSecret);
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            return http.post()
                    .uri(path)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Stripe request failed: " + e.getMessage());
        }
    }

    private JsonNode get(String path) {
        try {
            return http.get()
                    .uri(path)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Stripe request failed: " + e.getMessage());
        }
    }

    private ProviderPayment toProviderPayment(JsonNode node) {
        String id = node.path("id").asText();
        String status = node.path("status").asText();
        return switch (status) {
            case "succeeded" -> ProviderPayment.succeeded(id);
            case "requires_payment_method", "canceled" -> ProviderPayment.failed(id,
                    node.path("last_payment_error").path("code").asText(null),
                    node.path("last_payment_error").path("message").asText(null));
            default -> ProviderPayment.pending(id);
        };
    }

    private static String mapRefundReason(String reason) {
        return switch (reason) {
            case "duplicate" -> "duplicate";
            case "fraudulent" -> "fraudulent";
            default -> "requested_by_customer";
        };
    }

    private static String minorUnits(BigDecimal amount) {
        return amount.setScale(DEFAULT_CURRENCY_EXPONENT, RoundingMode.HALF_UP)
                .movePointRight(DEFAULT_CURRENCY_EXPONENT)
                .toBigIntegerExact()
                .toString();
    }
}
