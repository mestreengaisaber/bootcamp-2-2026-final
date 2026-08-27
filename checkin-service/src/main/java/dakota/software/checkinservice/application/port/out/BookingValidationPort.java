package dakota.software.checkinservice.application.port.out;

/**
 * Puerto-out para validar si una reserva está confirmada.
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 *
 * Consulta la tabla processed_events donde se guardan los
 * BookingConfirmedEvent recibidos de booking-service.
 */
public interface BookingValidationPort {
    boolean existsConfirmedBooking(Long bookingId);
}
