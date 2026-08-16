package dakota.software.flightservice.application.service;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.in.FlightUsecase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.domain.Flight;

import java.time.LocalDateTime;
import java.util.List;


public class FlightService implements FlightUsecase {

    private final FlightRepositoryPort flightRepositoryPort;

    public FlightService(FlightRepositoryPort flightRepositoryPort) {
        this.flightRepositoryPort = flightRepositoryPort;
    }

    @Override
    public List<Flight> searchByDateUseCase(SearchCommand command) {
        return flightRepositoryPort.findByDepartureDate(command.departureDate(), LocalDateTime.now());
    }

    @Override
    public List<Flight> searchByOriginDestinationUseCase(SearchCommand command) {
        return flightRepositoryPort.findByOriginAndDestination(
                command.originCode(), command.destinationCode(), LocalDateTime.now());
    }
}