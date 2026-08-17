package dakota.software.bookingservice.application.service;

import dakota.software.bookingservice.application.command.CreateBookingCommand;
import dakota.software.bookingservice.application.exception.BookingNotFoundException;
import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.application.port.out.BookingRepositoryPort;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.domain.Passenger;

public class BookingService implements BookingUsecase {

    private final BookingRepositoryPort bookingRepositoryPort;

    public BookingService(BookingRepositoryPort bookingRepositoryPort) {
        this.bookingRepositoryPort = bookingRepositoryPort;
    }

    @Override
    public Booking createBookingUseCase(CreateBookingCommand command) {
        Passenger passenger = new Passenger(
                command.passengerId(), command.passengerName(), command.passengerEmail());
        Booking booking = new Booking(passenger, command.flightId(), command.seats(), command.amount());
        return bookingRepositoryPort.save(booking);
    }

    @Override
    public Booking getBookingById(Long id) {
        return bookingRepositoryPort.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }
}