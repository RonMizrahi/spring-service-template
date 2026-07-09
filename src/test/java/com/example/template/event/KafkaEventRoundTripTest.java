package com.example.template.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for the Kafka component against an embedded broker, with messaging enabled.
 *
 * <p>Booting with {@code app.messaging.enabled=true} exercises the {@code @KafkaListener} placeholder
 * resolution (a regression guard: the context fails to start if {@code app.messaging.topic} is
 * misnamed), and the assertion verifies the publisher produces to the configured topic.</p>
 */
@SpringBootTest(properties = {
        "app.messaging.enabled=true",
        "app.messaging.topic=test-events",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "test-events")
class KafkaEventRoundTripTest {

    @Autowired
    private KafkaEventPublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void publishedMessageReachesTheConfiguredTopic() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("verifier-group", "true", broker);
        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer());

        try (Consumer<String, String> consumer = cf.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "test-events");

            publisher.sendMessage("k1", "hello-kafka");

            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("k1");
            assertThat(record.value()).isEqualTo("hello-kafka");
        }
    }
}
