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
                flight.getId(),
                flight.getFlightNumber(),
                flight.getOrigin().getCode(),
                flight.getOrigin().getCity(),
                flight.getDestination().getCode(),
                flight.getDestination().getCity(),
                flight.getDepartureAt(),
                flight.getArrivalAt(),
                flight.getPrice(),
                flight.getSeatInventory().getAvailableSeats());
    }
}