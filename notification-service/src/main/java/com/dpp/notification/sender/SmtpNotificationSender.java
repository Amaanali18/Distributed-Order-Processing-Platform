package com.dpp.notification.sender;

import com.dpp.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public void send(Notification notification) {
        if (notification.getRecipient() == null || notification.getRecipient().isBlank()) {
            throw new IllegalStateException("Notification " + notification.getId() + " has no recipient");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipient());
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());
        mailSender.send(message);
        log.info("Email notification {} sent to {}", notification.getId(), notification.getRecipient());
    }
}
