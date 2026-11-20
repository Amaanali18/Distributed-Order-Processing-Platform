package com.dpp.messaging;

import com.dpp.common.event.Topics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared Kafka error handler installed on every listener container factory.
 * <p>
 * Installation happens through {@link SmartInitializingSingleton}, not a
 * {@code BeanPostProcessor}. A BeanPostProcessor that depends on
 * {@link KafkaTemplate} is created before ordinary singletons, which drags
 * Kafka and Actuator metric infrastructure into early initialization and
 * silently skips JVM/process metric binders.
 */
@SuppressWarnings("rawtypes")
public class KafkaListenerErrorHandler extends DefaultErrorHandler implements SmartInitializingSingleton {

    private final ObjectProvider<ConcurrentKafkaListenerContainerFactory> containerFactories;

    public KafkaListenerErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                      long listenerRetries,
                                      long listenerBackoffMs,
                                      ObjectProvider<ConcurrentKafkaListenerContainerFactory> containerFactories) {
        super(deadLetterRecoverer(kafkaTemplate), backOff(listenerRetries, listenerBackoffMs));
        this.containerFactories = containerFactories;
    }

    @Override
    public void afterSingletonsInstantiated() {
        containerFactories.orderedStream()
                .forEach(factory -> factory.setCommonErrorHandler(this));
    }

    private static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(Topics.deadLetter(record.topic()), record.partition()));
    }

    private static ExponentialBackOff backOff(long listenerRetries, long listenerBackoffMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(listenerBackoffMs, 2.0);
        backOff.setMaxElapsedTime(listenerBackoffMs * (1L << Math.max(0, (int) listenerRetries)));
        return backOff;
    }
}
