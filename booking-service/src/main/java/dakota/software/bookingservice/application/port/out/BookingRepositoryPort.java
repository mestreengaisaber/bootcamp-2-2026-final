package dakota.software.bookingservice.application.port.out;

import dakota.software.bookingservice.domain.Booking;

import java.util.Optional;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    Optional<Booking> findById(Long id);
}
