package com.dpp.auth.service;

import com.dpp.auth.domain.RefreshToken;
import com.dpp.auth.domain.User;
import com.dpp.auth.domain.UserStatus;
import com.dpp.auth.repository.RefreshTokenRepository;
import com.dpp.auth.repository.UserRepository;
import com.dpp.auth.web.dto.LoginRequest;
import com.dpp.auth.web.dto.RefreshRequest;
import com.dpp.auth.web.dto.RegisterRequest;
import com.dpp.auth.web.dto.TokenResponse;
import com.dpp.auth.web.dto.UserResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.common.security.Roles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       TokenService tokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already registered");
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), Set.of(Roles.CUSTOMER));
        users.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokens.findByToken(request.refreshToken())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        if (!token.isActive(Instant.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token is expired or revoked");
        }
        token.revoke();
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private TokenResponse issue(User user) {
        String accessToken = tokens.createAccessToken(user);
        String refreshToken = tokens.newRefreshTokenValue();
        refreshTokens.save(RefreshToken.issue(refreshToken, user.getId(), tokens.refreshExpiry()));
        return TokenResponse.of(accessToken, refreshToken, tokens.accessTokenTtlSeconds());
    }
}
