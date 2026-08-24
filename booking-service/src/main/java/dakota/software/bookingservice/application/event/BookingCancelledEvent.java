package dakota.software.bookingservice.application.event;

import dakota.software.bookingservice.domain.Booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de integración: reserva cancelada (publicado por booking-service como compensación,
 * consumido por flight-service para liberar asientos y por notification-service).
 * Modelo sección 5.2 / 6.2: cuando el pago es DECLINED, booking se cancela y se publica
 * este evento para que flight libere los asientos reservados.
 * Contrato JSON documentado en la sección 6.2 del documento de arquitectura.
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

    public static BookingCancelledEvent from(Booking booking, String reason, String causationId) {
        return new BookingCancelledEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                String.valueOf(booking.getId()),
                VERSION,
                Instant.now().toString(),
                String.valueOf(booking.getId()),
                causationId,
                booking.getId(),
                booking.getFlightId(),
                booking.getSeats(),
                reason
        );
    }
}