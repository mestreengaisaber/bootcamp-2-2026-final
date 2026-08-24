package dakota.software.bookingservice.application.event;

/**
 * Evento de integración: resultado del procesamiento del pago (publicado por payment-service,
 * consumido por booking-service y notification-service).
 * Copia local (Caso B) del record de payment-service: el contrato real es el JSON + topic.
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
}