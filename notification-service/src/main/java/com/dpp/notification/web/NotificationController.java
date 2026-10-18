package com.dpp.notification.web;

import com.dpp.notification.service.NotificationQueryService;
import com.dpp.notification.web.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationQueryService notifications;

    public NotificationController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return notifications.listMine(CurrentUser.id(jwt), pageable);
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return notifications.get(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
    }
}
