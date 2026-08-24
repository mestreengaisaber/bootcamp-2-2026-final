package dakota.software.paymentservice.infraestructure.kafka;

import dakota.software.paymentservice.application.command.ProcessPaymentCommand;
import dakota.software.paymentservice.application.event.BookingCreatedEvent;
import dakota.software.paymentservice.application.port.in.ProcessPaymentUseCase;
import dakota.software.paymentservice.infraestructure.persistence.ProcessedEventEntity;
import dakota.software.paymentservice.infraestructure.persistence.ProcessedEventJpaRepository;
import dakota.software.paymentservice.domain.vo.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class PaymentKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaListener.class);
    private static final String DEFAULT_CURRENCY = "EUR";

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final ProcessedEventJpaRepository processedEventRepository;

    public PaymentKafkaListener(ProcessPaymentUseCase processPaymentUseCase,
                                ProcessedEventJpaRepository processedEventRepository) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "booking.created")
    @Transactional
    public void handleBookingCreated(BookingCreatedEvent event) {
        log.info("Received booking-created event {} for booking {}", event.eventId(), event.bookingId());

        if (isDuplicate(event.eventId())) {
            return;
        }

        PaymentMethod method = parsePaymentMethod(event.paymentMethod());
        if (method == null || event.amount() == null || event.passengerEmail() == null) {
            log.error("Rejecting malformed booking-created event - bookingId: {}, amount: {}, paymentMethod: {}. " +
                            "Event skipped, no payment processed.",
                    event.bookingId(), event.amount(), event.paymentMethod());
            return;
        }

        var command = new ProcessPaymentCommand(
                event.bookingId(),
                event.amount(),
                DEFAULT_CURRENCY,
                event.passengerEmail(),
                method,
                event.eventId()
        );

        var payment = processPaymentUseCase.process(command);
        log.info("Payment processed - id: {}, status: {}, bookingId: {}",
                payment.getTransactionId(), payment.getStatus(), event.bookingId());

        recordProcessed(event.eventId());
    }

    private PaymentMethod parsePaymentMethod(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown payment method '{}' in booking-created event", value);
            return null;
        }
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