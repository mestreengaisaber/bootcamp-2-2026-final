package dakota.software.flightservice.application.port.in;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.domain.Flight;

import java.util.List;

public interface GetFlightsUseCase {

    List<Flight> searchByDate(SearchCommand command);
    List<Flight> searchByOriginDestination(SearchCommand command);
    List<Flight> searchByFilters(SearchCommand command);

}
