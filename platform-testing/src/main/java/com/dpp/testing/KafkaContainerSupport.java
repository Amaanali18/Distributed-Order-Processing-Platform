package com.dpp.testing;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public final class KafkaContainerSupport {

    public static KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse(TestImages.KAFKA));
    }

    private KafkaContainerSupport() {
    }
}
