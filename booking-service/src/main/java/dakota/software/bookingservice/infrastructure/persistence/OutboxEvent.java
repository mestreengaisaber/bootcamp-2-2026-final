package dakota.software.bookingservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad del transactional outbox.
 * El evento se persiste en la MISMA transacción que la reserva (o su cambio de estado);
 * el relay lo publica a Kafka después y solo entonces se marca como publicado.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType,
                        String payload, Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public static OutboxEvent from(String eventId, String aggregateType, String aggregateId,
                                   String eventType, Object event, ObjectMapper objectMapper) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return new OutboxEvent(
                    UUID.fromString(eventId),
                    aggregateType,
                    aggregateId,
                    eventType,
                    payload,
                    Instant.now(),
                    null
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize outbox event payload for eventId " + eventId, e);
        }
    }

    public void markAsPublished() {
        this.publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}