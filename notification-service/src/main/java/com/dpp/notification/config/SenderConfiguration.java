package com.dpp.notification.config;

import com.dpp.notification.sender.LoggingNotificationSender;
import com.dpp.notification.sender.NotificationSender;
import com.dpp.notification.sender.SmtpNotificationSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class SenderConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dpp.notification", name = "sender", havingValue = "log", matchIfMissing = true)
    public NotificationSender loggingNotificationSender() {
        return new LoggingNotificationSender();
    }

    @Bean
    @ConditionalOnProperty(prefix = "dpp.notification", name = "sender", havingValue = "smtp")
    public NotificationSender smtpNotificationSender(JavaMailSender mailSender,
                                                     @Value("${dpp.notification.from:no-reply@dpp.local}") String from) {
        return new SmtpNotificationSender(mailSender, from);
    }
}
