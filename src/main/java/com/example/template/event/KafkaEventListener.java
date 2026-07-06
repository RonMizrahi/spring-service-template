package com.example.template.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes events from {@code app.kafka.topic}. Active only when {@code app.messaging.enabled=true}.
 *
 * <p>Processing exceptions are intentionally NOT caught here: letting them propagate hands the
 * record to Spring Kafka's {@code DefaultErrorHandler} (retry with back-off, and a
 * {@code DeadLetterPublishingRecoverer} if configured). Swallowing them would silently drop
 * messages.</p>
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaEventListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    @KafkaListener(id = "templateConsumer", topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id:template-group}")
    public void listen(String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        log.info("Received Kafka message: topic={}, key={}, value={}", topic, key, message);
        // Add real processing here; a thrown exception is handled by the container's error handler.
    }
}
