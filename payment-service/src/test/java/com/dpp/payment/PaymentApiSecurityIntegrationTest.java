package com.dpp.payment;

import com.dpp.payment.client.OrderClient;
import com.dpp.payment.client.dto.OrderView;
import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.repository.PaymentAttemptRepository;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
})
class PaymentApiSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PaymentService payments;

    @Autowired
    PaymentAttemptRepository attempts;

    @MockitoBean
    OrderClient orderClient;

    @BeforeEach
    void stubOrder() {
        when(orderClient.getOrder(any())).thenAnswer(inv -> {
            UUID orderId = inv.getArgument(0);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID customerId = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                    ? UUID.fromString(jwt.getSubject()) : UUID.randomUUID();
            return new OrderView(orderId, customerId, "PENDING", new BigDecimal("19.98"), "USD");
        });
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(UUID customerId) {
        return jwt().jwt(j -> j.subject(customerId.toString())
                        .claim("email", "customer@dpp.local")
                        .claim("roles", java.util.List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String paymentJson(UUID orderId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderId", orderId.toString(),
                "amount", new BigDecimal("19.98"),
                "currency", "USD"));
    }

    @Test
    void anonymousCannotCreatePayment() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCanCreatePayment() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(customer(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("PENDING"));
    }

    @Test
    void stripeWebhookIsPublicAndSettlesPayment() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderClient.getOrder(orderId)).thenReturn(
                new OrderView(orderId, customerId, "PENDING", new BigDecimal("19.98"), "USD"));
        PaymentResponse payment = payments.create(
                new CreatePaymentRequest(orderId, new BigDecimal("19.98"), "USD"),
                customerId, "key-" + UUID.randomUUID());

        String body = objectMapper.writeValueAsString(Map.of(
                "id", "evt_" + UUID.randomUUID(),
                "type", "payment_intent.succeeded",
                "data", Map.of("object", Map.of("id", payment.providerPaymentId()))));

        mockMvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(attempts.findById(payment.id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }
}
