package dakota.software.checkinservice.application.event;

import java.time.Instant;

/**
 * DTO que representa el evento BookingConfirmedEvent recibido de booking-service.
 * Se usa para guardar datos en processed_events y validar reserva confirmada.
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 */
public record BookingConfirmedEvent(
        String eventId,
        String eventType,
        Long bookingId,
        Long flightId,
        Long passengerId,
        int seats,
        String paymentMethod,
        Instant occurredAt
) {
    public static final String EVENT_TYPE = "BOOKING_CONFIRMED";
}
