package dakota.software.flightservice.application.event;

/**
 * Evento de integracion: reserva cancelada (publicado por booking-service
 * como compensacion cuando el pago es DECLINED, consumido por flight-service
 * para liberar los asientos reservados — cierre de US-006).
 * Copia local del contrato (doc de arquitectura seccion 6, Caso B).
 */
public record BookingCancelledEvent(
        String eventId,
        String eventType,
        String aggregateId,
        int version,
        String occurredAt,
        String correlationId,
        String causationId,
        Long bookingId,
        Long flightId,
        int seats,
        String reason
) {

    public static final String EVENT_TYPE = "BOOKING_CANCELLED";
    public static final int VERSION = 1;
}
