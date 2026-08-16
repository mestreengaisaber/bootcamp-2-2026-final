package dakota.software.flightservice.application.port.out;

import dakota.software.flightservice.domain.Flight;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


public interface FlightRepositoryPort {

    List<Flight> findByDepartureDate(LocalDate date, LocalDateTime from);

    List<Flight> findByOriginAndDestination(String originCode, String destinationCode, LocalDateTime from);
}