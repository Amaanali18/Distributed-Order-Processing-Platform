package com.dpp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(after = {KafkaAutoConfiguration.class, TransactionAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass({KafkaTemplate.class, NamedParameterJdbcTemplate.class})
@ConditionalOnProperty(prefix = "dpp.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new OutboxWriter(jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessedEventStore processedEventStore(NamedParameterJdbcTemplate jdbc) {
        return new ProcessedEventStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(NamedParameterJdbcTemplate jdbc,
                                   KafkaTemplate<Object, Object> kafkaTemplate,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${dpp.outbox.batch-size:50}") int batchSize,
                                   @Value("${dpp.outbox.max-attempts:5}") int maxAttempts,
                                   @Value("${dpp.outbox.retry-backoff-ms:2000}") long retryBackoffMs,
                                   @Value("${dpp.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        return new OutboxRelay(jdbc, kafkaTemplate, new TransactionTemplate(transactionManager),
                batchSize, maxAttempts, retryBackoffMs, sendTimeoutMs);
    }

    // Static factory method keeps this infrastructure bean out of configuration
    // class initialization. The handler installs itself only after all singletons
    // exist, so it must not be a BeanPostProcessor (see KafkaListenerErrorHandler).
    @Bean
    @ConditionalOnMissingBean
    public static KafkaListenerErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            ObjectProvider<ConcurrentKafkaListenerContainerFactory> containerFactories,
            @Value("${dpp.messaging.listener-retries:3}") long listenerRetries,
            @Value("${dpp.messaging.listener-backoff-ms:1000}") long listenerBackoffMs) {
        return new KafkaListenerErrorHandler(kafkaTemplate, listenerRetries, listenerBackoffMs,
                containerFactories);
    }
}
