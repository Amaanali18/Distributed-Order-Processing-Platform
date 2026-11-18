package com.dpp.payment.web;

import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ProviderPayment;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.service.WebhookEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    private static final String PAYLOAD = """
            {"id":"evt_1","type":"payment_intent.succeeded",
             "data":{"object":{"id":"pi_1"}}}
            """;

    @Mock
    private PaymentProvider provider;

    @Mock
    private PaymentService paymentService;

    @Mock
    private WebhookEventStore webhookEvents;

    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(provider, paymentService, webhookEvents, new ObjectMapper());
        when(provider.verifyWebhookSignature(anyString(), nullable(String.class))).thenReturn(true);
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutHandling() {
        when(webhookEvents.claim("evt_1", "payment_intent.succeeded"))
                .thenReturn(WebhookEventStore.Claim.DUPLICATE);

        ResponseEntity<Void> response = controller.handle(PAYLOAD, "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(paymentService);
        verify(webhookEvents, never()).markProcessed(anyString());
    }

    @Test
    void inProgressEventReturnsConflictForRedelivery() {
        when(webhookEvents.claim("evt_1", "payment_intent.succeeded"))
                .thenReturn(WebhookEventStore.Claim.IN_PROGRESS);

        ResponseEntity<Void> response = controller.handle(PAYLOAD, "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(paymentService);
    }

    @Test
    void successfulHandlingMarksEventProcessed() {
        when(webhookEvents.claim("evt_1", "payment_intent.succeeded"))
                .thenReturn(WebhookEventStore.Claim.NEW);

        ResponseEntity<Void> response = controller.handle(PAYLOAD, "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).handleProviderEvent(any(ProviderPayment.class), nullable(String.class));
        verify(webhookEvents).markProcessed("evt_1");
        verify(webhookEvents, never()).markFailed(anyString());
    }

    @Test
    void failedHandlingMarksEventFailedForRedelivery() {
        when(webhookEvents.claim("evt_1", "payment_intent.succeeded"))
                .thenReturn(WebhookEventStore.Claim.NEW);
        doThrow(new IllegalStateException("boom")).when(paymentService)
                .handleProviderEvent(any(ProviderPayment.class), nullable(String.class));

        assertThatThrownBy(() -> controller.handle(PAYLOAD, "sig"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        verify(webhookEvents).markFailed("evt_1");
        verify(webhookEvents, never()).markProcessed(anyString());
    }
}
