package dakota.software.checkinservice.infrastructure.web.dto;

import dakota.software.checkinservice.domain.CheckIn;

import java.time.Instant;
import java.time.LocalDateTime;

public record CheckInResponse(
        Long id,
        Long bookingId,
        Long flightId,
        Long passengerId,
        String status,
        String seatNumber,
        String gate,
        LocalDateTime boardingTime,
        Instant completedAt) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getBookingId(),
                checkIn.getFlightId(),
                checkIn.getPassengerId(),
                checkIn.getStatus().name(),
                checkIn.getBoardingPass() != null ? checkIn.getBoardingPass().seatNumber() : null,
                checkIn.getBoardingPass() != null ? checkIn.getBoardingPass().gate() : null,
                checkIn.getBoardingPass() != null ? checkIn.getBoardingPass().boardingTime() : null,
                checkIn.getCompletedAt());
    }
}
