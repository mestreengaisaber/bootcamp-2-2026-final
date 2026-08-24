package dakota.software.bookingservice.infrastructure.outbox;

import dakota.software.bookingservice.infrastructure.persistence.OutboxEvent;
import dakota.software.bookingservice.infrastructure.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;

/**
 * Relay del transactional outbox: cada 3s publica los eventos pendientes a Kafka.
 * Solo marca un evento como publicado cuando el send se confirmó; si falla,
 * el evento queda pendiente y se reintenta en el siguiente ciclo.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.of(
            "BOOKING_CREATED", "booking.created",
            "BOOKING_CANCELLED", "booking.cancelled"
    );

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxJpaRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            String topic = TOPIC_BY_EVENT_TYPE.get(event.getEventType());
            if (topic == null) {
                log.warn("No topic mapping for eventType '{}', outbox event {} will never be published",
                        event.getEventType(), event.getId());
                continue;
            }
            try {
                // Send asynchronously and mark as published only if successful.
                kafkaTemplate.send(topic, event.getPayload()).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send outbox event {} to topic {}", event.getId(), topic, ex);
                    } else {
                        event.markAsPublished();
                        outboxRepository.save(event);
                    }
                });
                log.info("Published outbox event {} ({}) to topic {}", event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                // Leave unpublished: it will be retried on the next relay cycle.
                log.error("Failed to publish outbox event {} to topic {}; will retry on next cycle",
                        event.getId(), topic, e);
            }
        }
    }
}