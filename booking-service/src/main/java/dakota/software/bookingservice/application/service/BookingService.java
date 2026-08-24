package dakota.software.bookingservice.application.service;

import dakota.software.bookingservice.application.command.CreateBookingCommand;
import dakota.software.bookingservice.application.event.PaymentProcessedEvent;
import dakota.software.bookingservice.application.exception.BookingNotFoundException;
import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.application.port.out.BookingEventPublisherPort;
import dakota.software.bookingservice.application.port.out.BookingRepositoryPort;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.domain.Passenger;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de booking.
 * Al crear: persiste la reserva y publica booking.created en la MISMA transacción (outbox).
 * Al aplicar el resultado del pago: confirma o cancela; si es DECLINED, publica booking.cancelled
 * (compensación) para que flight libere los asientos.
 * No sabe qué adaptador de persistencia ni qué publicador de eventos hay detrás.
 */
public class BookingService implements BookingUsecase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final BookingEventPublisherPort eventPublisher;

    public BookingService(BookingRepositoryPort bookingRepositoryPort, BookingEventPublisherPort eventPublisher) {
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Booking createBookingUseCase(CreateBookingCommand command) {
        Passenger passenger = new Passenger(
                command.passengerId(), command.passengerName(), command.passengerEmail());
        Booking booking = new Booking(
                passenger, command.flightId(), command.seats(), command.amount(), command.paymentMethod());
        Booking saved = bookingRepositoryPort.save(booking);
        eventPublisher.bookingCreated(saved);
        return saved;
    }

    @Override
    public Booking getBookingById(Long id) {
        return bookingRepositoryPort.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Override
    @Transactional
    public Booking applyPaymentResult(Long bookingId, String status, String reason, String causationId) {
        Booking booking = bookingRepositoryPort.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (PaymentProcessedEvent.STATUS_APPROVED.equals(status)) {
            booking.confirm();
        } else if (PaymentProcessedEvent.STATUS_DECLINED.equals(status)) {
            booking.cancel();
            eventPublisher.bookingCancelled(booking, reason, causationId);
        } else {
            throw new IllegalArgumentException("Unknown payment status: " + status);
        }
        return bookingRepositoryPort.save(booking);
    }
}