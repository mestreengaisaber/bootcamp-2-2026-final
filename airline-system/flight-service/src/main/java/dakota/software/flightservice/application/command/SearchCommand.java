package dakota.software.flightservice.application.command;

import dakota.software.flightservice.domain.Airport;

import java.time.LocalDateTime;

public record SearchCommand(Airport origin, Airport destination, LocalDateTime departureAt,LocalDateTime arrivalAt) {
}
