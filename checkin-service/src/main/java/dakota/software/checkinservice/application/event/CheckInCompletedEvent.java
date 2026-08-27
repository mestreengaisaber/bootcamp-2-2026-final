package dakota.software.checkinservice.application.event;

import dakota.software.checkinservice.domain.BoardingPass;
import dakota.software.checkinservice.domain.CheckIn;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record CheckInCompletedEvent(
        String eventId,
        String eventType,
        String aggregateId,
        int version,
        String occurredAt,
        String correlationId,
        String causationId,
        Long checkInId,
        Long bookingId,
        Long flightId,
        Long passengerId,
        String seatNumber,
        String gate,
        LocalDateTime boardingTime,
        Instant completedAt
) {

    public static final String EVENT_TYPE = "CHECKIN_COMPLETED";
    public static final int VERSION = 1;

    public static CheckInCompletedEvent from(CheckIn checkIn) {
        BoardingPass bp = checkIn.getBoardingPass();
        return new CheckInCompletedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                String.valueOf(checkIn.getId()),
                VERSION,
                Instant.now().toString(),
                String.valueOf(checkIn.getId()),
                null,
                checkIn.getId(),
                checkIn.getBookingId(),
                checkIn.getFlightId(),
                checkIn.getPassengerId(),
                bp != null ? bp.seatNumber() : null,
                bp != null ? bp.gate() : null,
                bp != null ? bp.boardingTime() : null,
                checkIn.getCompletedAt()
        );
    }
}
