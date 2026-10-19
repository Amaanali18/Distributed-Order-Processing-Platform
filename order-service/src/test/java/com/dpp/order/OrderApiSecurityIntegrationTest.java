package com.dpp.order;

import com.dpp.order.client.CatalogClient;
import com.dpp.order.client.dto.ProductView;
import com.dpp.order.client.dto.ReservationCommand;
import com.dpp.order.client.dto.ReservationView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class OrderApiSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    CatalogClient catalog;

    private UUID stubProduct() {
        UUID productId = UUID.randomUUID();
        when(catalog.getProduct(productId)).thenReturn(new ProductView(
                productId, "SKU-" + productId, "Widget", "desc", new BigDecimal("9.99"), "USD", "ACTIVE"));
        when(catalog.reserve(any(ReservationCommand.class))).thenReturn(new ReservationView(
                UUID.randomUUID(), UUID.randomUUID(), "RESERVED", List.of(), null));
        return productId;
    }

    private String orderJson(UUID productId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId.toString(), "quantity", 2))));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(UUID customerId) {
        return jwt().jwt(j -> j.subject(customerId.toString())
                        .claim("email", "customer@dpp.local")
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("email", "admin@dpp.local")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void anonymousCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(stubProduct())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanCreateOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(stubProduct())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(19.98));
    }

    @Test
    void customerCannotListAllOrders() throws Exception {
        mockMvc.perform(get("/admin/orders").with(customer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllOrders() throws Exception {
        mockMvc.perform(get("/admin/orders").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body = mockMvc.perform(post("/orders")
                        .with(customer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(stubProduct())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/orders/{id}", orderId).with(customer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyOrder() throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .with(customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(stubProduct())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/admin/orders/{id}", orderId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }
}
