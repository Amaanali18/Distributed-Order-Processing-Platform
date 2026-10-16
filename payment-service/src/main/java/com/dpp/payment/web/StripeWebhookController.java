package com.dpp.payment.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.provider.ProviderPaymentStatus;
import com.dpp.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentProvider provider;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public StripeWebhookController(PaymentProvider provider,
                                   PaymentService paymentService,
                                   ObjectMapper objectMapper) {
        this.provider = provider;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                       @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (!provider.verifyWebhookSignature(payload, signature)) {
            log.warn("Rejected Stripe webhook with invalid signature");
            return ResponseEntity.badRequest().build();
        }

        JsonNode event = read(payload);
        String type = event.path("type").asText();
        JsonNode intent = event.path("data").path("object");
        String providerPaymentId = intent.path("id").asText();
        if (providerPaymentId.isBlank()) {
            return ResponseEntity.ok().build();
        }

        ProviderPaymentStatus status = switch (type) {
            case "payment_intent.succeeded" -> ProviderPaymentStatus.SUCCEEDED;
            case "payment_intent.payment_failed", "payment_intent.canceled" -> ProviderPaymentStatus.FAILED;
            default -> null;
        };
        if (status == null) {
            log.debug("Ignoring unhandled Stripe event type {}", type);
            return ResponseEntity.ok().build();
        }

        ProviderPayment result = status == ProviderPaymentStatus.SUCCEEDED
                ? ProviderPayment.succeeded(providerPaymentId)
                : ProviderPayment.failed(providerPaymentId,
                        intent.path("last_payment_error").path("code").asText(null),
                        intent.path("last_payment_error").path("message").asText(null));

        paymentService.handleProviderEvent(result, CorrelationId.current());
        return ResponseEntity.ok().build();
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Stripe webhook payload", e);
        }
    }
}
