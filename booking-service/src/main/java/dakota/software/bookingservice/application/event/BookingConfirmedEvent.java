package dakota.software.bookingservice.application.event;

import dakota.software.bookingservice.domain.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de integracion: reserva confirmada (publicado por booking-service
 * cuando el pago es APROBADO, consumido por checkin-service para habilitar el check-in).
 * Modelo seccion 5.2 / 6.2: cuando el pago es APPROVED, booking se confirma y se publica
 * este evento para que checkin permita el check-in del pasajero.
 * Contrato JSON documentado en la seccion 6.2 del documento de arquitectura.
 */
public record BookingConfirmedEvent(
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
        BigDecimal amount
) {

    public static final String EVENT_TYPE = "BOOKING_CONFIRMED";
    public static final int VERSION = 1;

    public static BookingConfirmedEvent from(Booking booking, String causationId) {
        return new BookingConfirmedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                String.valueOf(booking.getId()),
                VERSION,
                Instant.now().toString(),
                String.valueOf(booking.getId()),
                causationId,
                booking.getId(),
                booking.getPassenger().passengerId(),
                booking.getFlightId(),
                booking.getSeats(),
                booking.getAmount()
        );
    }
}
