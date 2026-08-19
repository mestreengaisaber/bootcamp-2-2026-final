package dakota.software.flightservice.infrastructure.web;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.in.FlightUsecase;
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

    private final FlightUsecase flightUsecase;

    public FlightController(FlightUsecase flightUsecase) {

        this.flightUsecase = flightUsecase;
    }

    @GetMapping
    public List<FlightResponse> search(@Valid @ModelAttribute FlightSearchRequest request) {
        SearchCommand command = new SearchCommand(request.origin(), request.destination(), request.departureDate());
        List<Flight> flights = flightUsecase.searchByFilters(command);
        return flights.stream().map(FlightResponse::from).toList();
    }
}