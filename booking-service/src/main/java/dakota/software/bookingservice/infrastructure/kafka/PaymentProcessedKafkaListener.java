package dakota.software.bookingservice.infrastructure.kafka;

import dakota.software.bookingservice.application.event.PaymentProcessedEvent;
import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.infrastructure.persistence.ProcessedEventEntity;
import dakota.software.bookingservice.infrastructure.persistence.ProcessedEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumidor del resultado del pago: ramifica por status (APPROVED → confirmar, DECLINED → cancelar
 * + compensación booking.cancelled). Idempotente vía processed_events (PK = eventId).
 */
@Component
public class PaymentProcessedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedKafkaListener.class);

    private final BookingUsecase bookingUsecase;
    private final ProcessedEventJpaRepository processedEventRepository;

    public PaymentProcessedKafkaListener(BookingUsecase bookingUsecase,
                                         ProcessedEventJpaRepository processedEventRepository) {
        this.bookingUsecase = bookingUsecase;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "payment.processed")
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Received payment-processed event {} for booking {} (status {})",
                event.eventId(), event.bookingId(), event.status());

        if (isDuplicate(event.eventId())) {
            return;
        }

        bookingUsecase.applyPaymentResult(
                event.bookingId(),
                event.status(),
                event.reason(),
                event.eventId());

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
            // Another delivery already recorded this eventId (PK conflict) -> treat as processed
            log.warn("Processed event {} already recorded (race), ignoring", eventId);
        }
    }
}