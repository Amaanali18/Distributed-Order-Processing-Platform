package com.dpp.auth.web.dto;

import com.dpp.auth.domain.User;

import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, Set<String> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRoles());
    }
}
