package com.dpp.auth.bootstrap;

import com.dpp.auth.domain.User;
import com.dpp.auth.repository.UserRepository;
import com.dpp.common.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          @Value("${dpp.bootstrap.admin-email:}") String adminEmail,
                          @Value("${dpp.bootstrap.admin-password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: set DPP_BOOTSTRAP_ADMIN_EMAIL and DPP_BOOTSTRAP_ADMIN_PASSWORD to seed an administrator");
            return;
        }
        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            log.info("Admin bootstrap skipped: {} already exists", email);
            return;
        }
        users.save(User.create(email, passwordEncoder.encode(adminPassword), Set.of(Roles.ADMIN, Roles.CUSTOMER)));
        log.info("Bootstrapped administrator account {}", email);
    }
}
