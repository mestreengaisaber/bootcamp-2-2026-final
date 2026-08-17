package dakota.software.bookingservice.infrastructure.web.dto;

import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.domain.BookingStatus;

import java.math.BigDecimal;

public record BookingResponse(Long id,
                              String passengerId,
                              String passengerName,
                              String passengerEmail,
                              Long flightId,
                              int seats,
                              BigDecimal amount,
                              BookingStatus status) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getPassenger().passengerId(),
                booking.getPassenger().name(),
                booking.getPassenger().email(),
                booking.getFlightId(),
                booking.getSeats(),
                booking.getAmount(),
                booking.getStatus());
    }
}