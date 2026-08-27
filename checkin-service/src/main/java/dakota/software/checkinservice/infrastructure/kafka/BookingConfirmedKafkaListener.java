package dakota.software.checkinservice.infrastructure.kafka;

import dakota.software.checkinservice.infrastructure.persistence.ProcessedEventEntity;
import dakota.software.checkinservice.infrastructure.persistence.ProcessedEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Consume booking.confirmed de Kafka y guarda los datos en processed_events.
 * Esto permite validar que la reserva está confirmada antes de hacer check-in.
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 */
@Component
public class BookingConfirmedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedKafkaListener.class);

    private final ProcessedEventJpaRepository processedEventRepository;

    public BookingConfirmedKafkaListener(ProcessedEventJpaRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "booking.confirmed",
            properties = "spring.json.value.default.type=java.util.Map"
    )
    @SuppressWarnings("unchecked")
    public void handleBookingConfirmed(Map<String, Object> rawEvent) {
        String eventId = (String) rawEvent.get("eventId");
        log.info("Received booking.confirmed event {}", eventId);

        if (eventId == null) {
            log.error("Received booking.confirmed event with null eventId, skipping");
            return;
        }

        if (isDuplicate(eventId)) {
            return;
        }

        // Guardar datos del evento para validar reserva confirmada
        // passengerId es String en el dominio ("passenger"), no Long — la DB tiene BIGINT
        // pero no lo necesitamos para la validación de check-in (solo bookingId + eventType)
        ProcessedEventEntity entity = new ProcessedEventEntity(
                eventId,
                "BOOKING_CONFIRMED",
                toLong(rawEvent.get("bookingId")),
                toLong(rawEvent.get("flightId")),
                null,
                Instant.now()
        );
        processedEventRepository.save(entity);
        log.info("Booking confirmed event {} recorded for booking {}",
                eventId, entity.getBookingId());
    }

    private boolean isDuplicate(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event {} ignored", eventId);
            return true;
        }
        return false;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
}
