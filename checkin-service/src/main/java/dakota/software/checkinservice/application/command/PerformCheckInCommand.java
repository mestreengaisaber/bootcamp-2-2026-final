package dakota.software.checkinservice.application.command;

public record PerformCheckInCommand(Long bookingId, Long flightId, Long passengerId) {
}
