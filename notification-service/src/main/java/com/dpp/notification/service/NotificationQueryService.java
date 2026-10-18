package com.dpp.notification.service;

import com.dpp.common.error.ApiException;
import com.dpp.notification.domain.Notification;
import com.dpp.notification.domain.NotificationStatus;
import com.dpp.notification.repository.NotificationRepository;
import com.dpp.notification.web.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public Page<NotificationResponse> listMine(UUID customerId, Pageable pageable) {
        return notifications.findByCustomerId(customerId, pageable).map(NotificationResponse::from);
    }

    public Page<NotificationResponse> listAll(NotificationStatus status, Pageable pageable) {
        Page<Notification> page = status == null
                ? notifications.findAll(pageable)
                : notifications.findByStatus(status, pageable);
        return page.map(NotificationResponse::from);
    }

    public NotificationResponse get(UUID id, UUID customerId, boolean admin) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification not found: " + id));
        if (!admin && !customerId.equals(notification.getCustomerId())) {
            throw ApiException.forbidden("Notification " + id + " does not belong to the caller");
        }
        return NotificationResponse.from(notification);
    }
}
