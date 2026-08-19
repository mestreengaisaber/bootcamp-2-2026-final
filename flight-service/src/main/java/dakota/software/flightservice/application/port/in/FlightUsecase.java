package dakota.software.flightservice.application.port.in;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.domain.Flight;

import java.util.List;

public interface FlightUsecase {

   List <Flight> searchByDateUseCase(SearchCommand command);
   List<Flight> searchByOriginDestinationUseCase(SearchCommand command);


   List<Flight> searchByFilters(SearchCommand command);

   //lock
   void reserveSeatsUseCase(Long flightId, int seats);
}