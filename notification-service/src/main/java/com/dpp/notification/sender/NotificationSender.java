package com.dpp.notification.sender;

import com.dpp.notification.domain.Notification;

public interface NotificationSender {

    String channel();

    void send(Notification notification);
}
