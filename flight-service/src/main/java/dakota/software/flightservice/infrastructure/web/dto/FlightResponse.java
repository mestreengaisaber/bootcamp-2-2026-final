package dakota.software.flightservice.infrastructure.web.dto;

import dakota.software.flightservice.domain.Flight;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlightResponse(Long id,
                             String flightNumber,
                             String originCode,
                             String originCity,
                             String destinationCode,
                             String destinationCity,
                             LocalDateTime departureAt,
                             LocalDateTime arrivalAt,
                             BigDecimal price,
                             int availableSeats) {

    public static FlightResponse from(Flight flight) {
        return new FlightResponse(
                flight.id(),
                flight.flightNumber(),
                flight.origin().code(),
                flight.origin().city(),
                flight.destination().code(),
                flight.destination().city(),
                flight.departureAt(),
                flight.arrivalAt(),
                flight.price(),
                flight.seatInventory().getAvailableSeats());
    }
}