package dakota.software.bookingservice.infrastructure.persistence;

import dakota.software.bookingservice.application.port.out.BookingRepositoryPort;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.domain.Passenger;

import java.util.Optional;

public class BookingPersistenceAdapter implements BookingRepositoryPort {

    private final BookingJpaRepository bookingJpaRepository;

    public BookingPersistenceAdapter(BookingJpaRepository bookingJpaRepository) {
        this.bookingJpaRepository = bookingJpaRepository;
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity saved = bookingJpaRepository.save(toEntity(booking));
        return toDomain(saved);
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return bookingJpaRepository.findById(id).map(this::toDomain);
    }

    private BookingEntity toEntity(Booking booking) {
        return new BookingEntity(
                booking.getPassenger().passengerId(),
                booking.getPassenger().name(),
                booking.getPassenger().email(),
                booking.getFlightId(),
                booking.getSeats(),
                booking.getAmount(),
                booking.getStatus());
    }

    private Booking toDomain(BookingEntity entity) {
        Passenger passenger = new Passenger(
                entity.getPassengerId(),
                entity.getPassengerName(),
                entity.getPassengerEmail());
        return new Booking(
                entity.getId(),
                passenger,
                entity.getFlightId(),
                entity.getSeats(),
                entity.getAmount(),
                entity.getStatus());
    }
}