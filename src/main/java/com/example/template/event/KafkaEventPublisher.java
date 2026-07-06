package com.example.template.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes String events to Kafka. Active only when {@code app.messaging.enabled=true}.
 * Publisher and listener share the single {@code app.kafka.topic} property so they round-trip.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@Slf4j
public class KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate,
        @Value("${app.kafka.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendMessage(String key, String value) {
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, value);
        future.thenAccept(result -> {
            log.info("Produced event to topic {}: key = {} value = {}", topic, key, value);
        }).exceptionally(ex -> {
            log.error("Failed to send message to Kafka", ex);
            return null;
        });
    }
}
