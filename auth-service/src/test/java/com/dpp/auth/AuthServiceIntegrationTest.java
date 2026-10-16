package com.dpp.auth;

import com.dpp.auth.repository.RefreshTokenRepository;
import com.dpp.auth.repository.UserRepository;
import com.dpp.auth.service.AuthService;
import com.dpp.auth.web.dto.LoginRequest;
import com.dpp.auth.web.dto.RefreshRequest;
import com.dpp.auth.web.dto.RegisterRequest;
import com.dpp.auth.web.dto.TokenResponse;
import com.dpp.auth.web.dto.UserResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "dpp.bootstrap.admin-email=",
        "dpp.bootstrap.admin-password="
})
class AuthServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Passw0rd!123";

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository users;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    JwtDecoder jwtDecoder;

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@dpp.local";
    }

    @Test
    void registerPersistsCustomerAccount() {
        String email = newEmail();

        UserResponse response = authService.register(new RegisterRequest(email, PASSWORD));

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.roles()).containsExactly("CUSTOMER");
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() {
        String email = newEmail();
        authService.register(new RegisterRequest(email, PASSWORD));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(email.toUpperCase(Locale.ROOT), PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void loginIssuesDecodableAccessTokenAndRefreshToken() {
        String email = newEmail();
        authService.register(new RegisterRequest(email, PASSWORD));

        TokenResponse tokens = authService.login(new LoginRequest(email, PASSWORD));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isPositive();

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void loginFailsWithWrongPassword() {
        String email = newEmail();
        authService.register(new RegisterRequest(email, PASSWORD));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "not-the-password")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void loginFailsForUnknownEmail() {
        assertThatThrownBy(() -> authService.login(new LoginRequest(newEmail(), PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void refreshRotatesTokenAndRevokesThePreviousOne() {
        String email = newEmail();
        authService.register(new RegisterRequest(email, PASSWORD));
        TokenResponse first = authService.login(new LoginRequest(email, PASSWORD));

        TokenResponse second = authService.refresh(new RefreshRequest(first.refreshToken()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
        assertThat(refreshTokens.findByToken(first.refreshToken()))
                .hasValueSatisfying(token -> assertThat(token.isActive(Instant.now())).isFalse());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(first.refreshToken())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void currentUserReturnsRegisteredProfile() {
        String email = newEmail();
        UserResponse registered = authService.register(new RegisterRequest(email, PASSWORD));

        UserResponse current = authService.currentUser(registered.id());

        assertThat(current.id()).isEqualTo(registered.id());
        assertThat(current.email()).isEqualTo(email);
        assertThat(current.roles()).containsExactly("CUSTOMER");
    }
}
