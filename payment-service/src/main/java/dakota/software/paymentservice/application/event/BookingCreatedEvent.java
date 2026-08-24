package dakota.software.paymentservice.application.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de integración: reserva creada (publicado por booking-service, consumido por payment-service).
 * Envelope + payload aplanados en un único record (mismo patrón que el proyecto de referencia).
 * passengerEmail y paymentMethod alimentan el procesamiento del pago (decisión 2026-08-19).
 * Contrato JSON documentado en la sección 6.2 del documento de arquitectura.
 */
public record BookingCreatedEvent(
        String eventId,
        String eventType,
        String aggregateId,
        int version,
        String occurredAt,
        String correlationId,
        String causationId,
        Long bookingId,
        String passengerId,
        Long flightId,
        int seats,
        BigDecimal amount,
        String passengerEmail,
        String paymentMethod,
        Instant createdAt
) {

    public static final String EVENT_TYPE = "BOOKING_CREATED";
    public static final int VERSION = 1;
}