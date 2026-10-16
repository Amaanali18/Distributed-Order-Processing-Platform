package com.dpp.auth;

import com.dpp.auth.repository.UserRepository;
import com.dpp.auth.service.AuthService;
import com.dpp.auth.web.dto.LoginRequest;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "dpp.bootstrap.admin-email=root@dpp.local",
        "dpp.bootstrap.admin-password=RootPass123!"
})
class AdminBootstrapIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    AuthService authService;

    @Test
    void seedsAdministratorWithAdminAndCustomerRoles() {
        var admin = users.findByEmailIgnoreCase("root@dpp.local").orElseThrow();

        assertThat(admin.getRoles()).containsExactlyInAnyOrder("ADMIN", "CUSTOMER");

        assertThat(authService.login(new LoginRequest("root@dpp.local", "RootPass123!")).accessToken()).isNotBlank();
    }
}
