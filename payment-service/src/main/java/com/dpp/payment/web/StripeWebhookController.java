package com.dpp.payment.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.provider.ProviderPaymentStatus;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.service.WebhookEventStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
    private final WebhookEventStore webhookEvents;
    private final ObjectMapper objectMapper;

    public StripeWebhookController(PaymentProvider provider,
                                   PaymentService paymentService,
                                   WebhookEventStore webhookEvents,
                                   ObjectMapper objectMapper) {
        this.provider = provider;
        this.paymentService = paymentService;
        this.webhookEvents = webhookEvents;
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
        if (event == null) {
            return ResponseEntity.badRequest().build();
        }
        String eventId = event.path("id").asText();
        if (eventId.isBlank()) {
            log.warn("Rejected Stripe webhook without an event id");
            return ResponseEntity.badRequest().build();
        }
        String type = event.path("type").asText();

        WebhookEventStore.Claim claim = webhookEvents.claim(eventId, type);
        if (claim == WebhookEventStore.Claim.DUPLICATE) {
            log.debug("Ignoring duplicate Stripe webhook {}", eventId);
            return ResponseEntity.ok().build();
        }
        if (claim == WebhookEventStore.Claim.IN_PROGRESS) {
            // Ask the provider to redeliver instead of silently dropping the event.
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        JsonNode intent = event.path("data").path("object");
        String providerPaymentId = intent.path("id").asText();
        if (providerPaymentId.isBlank()) {
            return ignore(eventId);
        }

        ProviderPaymentStatus status = switch (type) {
            case "payment_intent.succeeded" -> ProviderPaymentStatus.SUCCEEDED;
            case "payment_intent.payment_failed", "payment_intent.canceled" -> ProviderPaymentStatus.FAILED;
            default -> null;
        };
        if (status == null) {
            log.debug("Ignoring unhandled Stripe event type {}", type);
            return ignore(eventId);
        }

        ProviderPayment result = status == ProviderPaymentStatus.SUCCEEDED
                ? ProviderPayment.succeeded(providerPaymentId)
                : ProviderPayment.failed(providerPaymentId,
                        intent.path("last_payment_error").path("code").asText(null),
                        intent.path("last_payment_error").path("message").asText(null));

        try {
            paymentService.handleProviderEvent(result, CorrelationId.current());
            webhookEvents.markProcessed(eventId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            webhookEvents.markFailed(eventId);
            throw e;
        }
    }

    private ResponseEntity<Void> ignore(String eventId) {
        try {
            webhookEvents.markProcessed(eventId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            webhookEvents.markFailed(eventId);
            throw e;
        }
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Rejected malformed Stripe webhook payload");
            return null;
        }
    }
}
