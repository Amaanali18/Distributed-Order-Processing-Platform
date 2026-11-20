package com.dpp.order;

import com.dpp.messaging.KafkaListenerErrorHandler;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the interaction between the Kafka {@link KafkaListenerErrorHandler}
 * BeanPostProcessor and Actuator metric binding. A non-static {@code @Bean}
 * factory for that post-processor initializes the MeterRegistry too early and
 * JVM/process binders are silently skipped, so this context-level check keeps
 * {@code /actuator/prometheus} complete.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000",
        "dpp.outbox.poll-interval-ms=3600000"
})
class MetricsRegistryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    MeterRegistry meterRegistry;

    @MockitoBean
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void standardJvmAndProcessMetersAreRegistered() {
        assertThat(meterRegistry.find("jvm.memory.used").meters()).isNotEmpty();
        assertThat(meterRegistry.find("process.cpu.usage").meters()).isNotEmpty();
    }
}
