package dakota.software.bookingservice.application.port.out;

import dakota.software.bookingservice.domain.Booking;

/**
 * Puerto de salida: publicación de eventos de reserva.
 * El caso de uso delega aquí; la implementación concreta (outbox transaccional) vive en infraestructura.
 * Modelo sección 5.2: se publica booking.created al crear y booking.cancelled como compensación
 * cuando el pago es DECLINED (liberar asientos en flight-service).
 */
public interface BookingEventPublisherPort {

    void bookingCreated(Booking booking);

    void bookingCancelled(Booking booking, String reason, String causationId);

    void bookingConfirmed(Booking booking, String causationId);
}