package dakota.software.flightservice.infrastructure.web;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.in.GetFlightsUseCase;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.infrastructure.web.dto.FlightResponse;
import dakota.software.flightservice.infrastructure.web.dto.FlightSearchRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final GetFlightsUseCase getFlightsUseCase;

    public FlightController(GetFlightsUseCase getFlightsUseCase) {
        this.getFlightsUseCase = getFlightsUseCase;
    }
    @GetMapping
    public List<FlightResponse> search(@Valid @ModelAttribute FlightSearchRequest request) {
        SearchCommand command = new SearchCommand(request.origin(), request.destination(), request.departureDate());
        List<Flight> flights = getFlightsUseCase.searchByFilters(command);
        return flights.stream().map(FlightResponse::from).toList();
    }
}