package dakota.software.paymentservice.infraestructure.adapter;

import dakota.software.paymentservice.application.event.PaymentProcessedEvent;
import dakota.software.paymentservice.application.port.out.PaymentEventPublisherPort;
import dakota.software.paymentservice.infraestructure.persistence.OutboxEvent;
import dakota.software.paymentservice.infraestructure.persistence.OutboxJpaRepository;
import dakota.software.paymentservice.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador del puerto de publicación: persiste el evento de resultado en el outbox
 * dentro de la misma transacción que el pago. El OutboxRelay lo publicará a Kafka después.
 * Modelo sección 5.3: un único evento PaymentProcessedEvent con campo status.
 */
public class PaymentEventOutboxAdapter implements PaymentEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventOutboxAdapter.class);
    private static final String AGGREGATE_TYPE = "PAYMENT";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventOutboxAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void paymentProcessed(Payment payment, Long bookingId, String causationId) {
        PaymentProcessedEvent event = PaymentProcessedEvent.from(payment, bookingId, causationId);
        saveOutbox(event.eventId(), event.aggregateId(), event.eventType(), event);
        log.info("Payment-processed event {} stored in outbox for booking {} (status {})",
                event.eventId(), bookingId, event.status());
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