package dakota.software.bookingservice.infrastructure.adapter;

import dakota.software.bookingservice.application.event.BookingCancelledEvent;
import dakota.software.bookingservice.application.event.BookingCreatedEvent;
import dakota.software.bookingservice.application.port.out.BookingEventPublisherPort;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.infrastructure.persistence.OutboxEvent;
import dakota.software.bookingservice.infrastructure.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador del puerto de publicación: persiste los eventos de reserva en el outbox
 * dentro de la misma transacción que el cambio del agregado. El OutboxRelay los publica a Kafka después.
 */
public class BookingEventOutboxAdapter implements BookingEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(BookingEventOutboxAdapter.class);
    private static final String AGGREGATE_TYPE = "BOOKING";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BookingEventOutboxAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void bookingCreated(Booking booking) {
        BookingCreatedEvent event = BookingCreatedEvent.from(booking);
        saveOutbox(event.eventId(), event.aggregateId(), event.eventType(), event);
        log.info("Booking-created event {} stored in outbox for booking {}", event.eventId(), booking.getId());
    }

    @Override
    public void bookingCancelled(Booking booking, String reason, String causationId) {
        BookingCancelledEvent event = BookingCancelledEvent.from(booking, reason, causationId);
        saveOutbox(event.eventId(), event.aggregateId(), event.eventType(), event);
        log.info("Booking-cancelled event {} stored in outbox for booking {} (reason {})",
                event.eventId(), booking.getId(), reason);
    }

    private void saveOutbox(String eventId, String aggregateId, String eventType, Object event) {
        outboxRepository.save(OutboxEvent.from(
                eventId,
                AGGREGATE_TYPE,
                aggregateId,
                eventType,
                event,
                objectMapper
        ));
    }
}