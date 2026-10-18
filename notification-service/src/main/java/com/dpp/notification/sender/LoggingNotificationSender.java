package com.dpp.notification.sender;

import com.dpp.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public String channel() {
        return "log";
    }

    @Override
    public void send(Notification notification) {
        log.info("[notification] to={} type={} subject={} body={}",
                notification.getRecipient(), notification.getEventType(),
                notification.getSubject(), notification.getBody());
    }
}
