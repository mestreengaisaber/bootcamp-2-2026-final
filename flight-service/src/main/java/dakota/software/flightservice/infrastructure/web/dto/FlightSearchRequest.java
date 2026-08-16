package dakota.software.flightservice.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record FlightSearchRequest(
        @Pattern(regexp = "[A-Z]{3}", message = "origin must be a 3-letter IATA code") String origin,
        @Pattern(regexp = "[A-Z]{3}", message = "destination must be a 3-letter IATA code") String destination,
        LocalDate departureDate) {
}