package dakota.software.checkinservice.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record PerformCheckInRequest(
        @NotNull Long bookingId,
        @NotNull Long flightId,
        @NotNull Long passengerId) {
}
