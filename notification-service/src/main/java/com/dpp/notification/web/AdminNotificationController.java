package com.dpp.notification.web;

import com.dpp.notification.domain.NotificationStatus;
import com.dpp.notification.service.NotificationQueryService;
import com.dpp.notification.web.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final NotificationQueryService notifications;

    public AdminNotificationController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status,
                                           Pageable pageable) {
        return notifications.listAll(status, pageable);
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return notifications.get(id, CurrentUser.id(jwt), true);
    }
}
