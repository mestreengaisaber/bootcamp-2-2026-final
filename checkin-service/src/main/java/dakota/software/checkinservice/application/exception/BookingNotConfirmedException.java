package dakota.software.checkinservice.application.exception;

/**
 * Se lanza cuando se intenta hacer check-in para una reserva
 * que no fue confirmada (no existe en processed_events).
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 */
public class BookingNotConfirmedException extends RuntimeException {
    public BookingNotConfirmedException(Long bookingId) {
        super("Booking " + bookingId + " is not confirmed. Check-in cannot proceed without a confirmed booking.");
    }
}
