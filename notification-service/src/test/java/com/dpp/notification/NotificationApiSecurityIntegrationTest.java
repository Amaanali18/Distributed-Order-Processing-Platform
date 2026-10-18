package com.dpp.notification;

import com.dpp.notification.domain.Notification;
import com.dpp.notification.repository.NotificationRepository;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class NotificationApiSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationRepository notificationRepository;

    private UUID seedNotification(UUID customerId) {
        Notification notification = Notification.pending(UUID.randomUUID(), "OrderCreated", UUID.randomUUID(),
                customerId, "customer@dpp.local", "log", "Order received", "Your order was received.");
        return notificationRepository.save(notification).getId();
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
    void anonymousCannotListNotifications() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerListsOwnNotifications() throws Exception {
        UUID customerId = UUID.randomUUID();
        seedNotification(customerId);

        mockMvc.perform(get("/notifications").with(customer(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].customerId").value(customerId.toString()));
    }

    @Test
    void customerCannotReadAnotherCustomersNotification() throws Exception {
        UUID notificationId = seedNotification(UUID.randomUUID());

        mockMvc.perform(get("/notifications/{id}", notificationId).with(customer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotListAllNotifications() throws Exception {
        mockMvc.perform(get("/admin/notifications").with(customer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllNotifications() throws Exception {
        seedNotification(UUID.randomUUID());

        mockMvc.perform(get("/admin/notifications").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCanReadAnyNotification() throws Exception {
        UUID notificationId = seedNotification(UUID.randomUUID());

        mockMvc.perform(get("/admin/notifications/{id}", notificationId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()));
    }
}
