package dakota.software.flightservice.infrastructure.kafka;

import dakota.software.flightservice.application.event.BookingCreatedEvent;
import dakota.software.flightservice.application.port.in.ReserveSeatsUseCase;
import dakota.software.flightservice.infrastructure.persistence.ProcessedEventEntity;
import dakota.software.flightservice.infrastructure.persistence.ProcessedEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class BookingCreatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCreatedKafkaListener.class);

    private final ReserveSeatsUseCase reserveSeatsUseCase;
    private final ProcessedEventJpaRepository processedEventRepository;

    public BookingCreatedKafkaListener(ReserveSeatsUseCase reserveSeatsUseCase,
                                       ProcessedEventJpaRepository processedEventRepository) {
        this.reserveSeatsUseCase = reserveSeatsUseCase;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "booking.created",
            properties = "spring.json.value.default.type=dakota.software.flightservice.application.event.BookingCreatedEvent"
    )
    @Transactional
    public void handleBookingCreated(BookingCreatedEvent event) {
        log.info("Received booking-created event {} for booking {} flight {}",
                event.eventId(), event.bookingId(), event.flightId());

        if (isDuplicate(event.eventId())) {
            return;
        }

        // Validación básica: flightId y seats son obligatorios
        if (event.flightId() == null || event.seats() <= 0) {
            log.error("Rejecting malformed booking-created event - flightId: {}, seats: {}. Event skipped.",
                    event.flightId(), event.seats());
            return;
        }

        // Procesar: reservar asientos
        reserveSeatsUseCase.reserve(event.flightId(), event.seats());
        log.info("Seats reserved for flight {} (booking {})", event.flightId(), event.bookingId());

        // Registrar como procesado
        recordProcessed(event.eventId());
    }

    private boolean isDuplicate(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event {} ignored", eventId);
            return true;
        }
        return false;
    }

    private void recordProcessed(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEventEntity(eventId, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            // Otra entrega ya registró este eventId (PK conflict) -> tratar como procesado
            log.warn("Processed event {} already recorded (race), ignoring", eventId);
        }
    }
}