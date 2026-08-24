package dakota.software.paymentservice.application.event;

import dakota.software.paymentservice.domain.Payment;

import java.util.UUID;

/**
 * Evento de integración: resultado del procesamiento del pago (UN evento con campo status).
 * Envelope + payload aplanados en un único record.
 * Modelo sección 5.3: APPROVED → booking CONFIRMED; DECLINED → booking CANCELLED (compensación).
 * reason solo se rellena cuando status = DECLINED.
 * Contrato JSON documentado en la sección 6.2 del documento de arquitectura.
 */
public record PaymentProcessedEvent(
        String eventId,
        String eventType,
        String aggregateId,
        int version,
        String occurredAt,
        String correlationId,
        String causationId,
        String paymentId,
        Long bookingId,
        String status,
        String reason
) {

    public static final String EVENT_TYPE = "PAYMENT_PROCESSED";
    public static final int VERSION = 1;
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DECLINED = "DECLINED";

    public static PaymentProcessedEvent from(Payment payment, Long bookingId, String causationId) {
        boolean approved = payment.isCompleted();
        return new PaymentProcessedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                String.valueOf(bookingId),
                VERSION,
                payment.getProcessedAt().toString(),
                String.valueOf(bookingId),
                causationId,
                payment.getTransactionId(),
                bookingId,
                approved ? STATUS_APPROVED : STATUS_DECLINED,
                approved ? null : payment.getProviderResponse()
        );
    }
}