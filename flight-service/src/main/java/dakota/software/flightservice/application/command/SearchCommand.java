package dakota.software.flightservice.application.command;

import java.time.LocalDate;

public record SearchCommand(String originCode, String destinationCode, LocalDate departureDate) {
}