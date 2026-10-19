package com.dpp.order.web;

import com.dpp.common.security.Roles;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public final class CurrentUser {

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static String email(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email == null ? "" : email;
    }

    public static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(Roles.ADMIN);
    }

    private CurrentUser() {
    }
}
