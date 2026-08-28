package dakota.software.checkinservice.infrastructure.persistence;

import dakota.software.checkinservice.application.port.out.BookingValidationPort;

/**
 * Adapter que implementa BookingValidationPort.
 * Consulta processed_events para validar si una reserva fue confirmada.
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 */
public class BookingValidationAdapter implements BookingValidationPort {

    private final ProcessedEventJpaRepository processedEventRepository;

    public BookingValidationAdapter(ProcessedEventJpaRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Override
    public boolean existsConfirmedBooking(Long bookingId) {
        return !processedEventRepository.findByEventTypeAndBookingId("BOOKING_CONFIRMED", bookingId).isEmpty();
    }
}
