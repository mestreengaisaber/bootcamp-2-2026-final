package dakota.software.flightservice.application.service;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.in.FlightUsecase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.domain.SeatInventory;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public List<Flight> searchByFilters(SearchCommand command) {

        if (command.originCode() != null && command.destinationCode() != null) {
            return flightRepositoryPort.findByOriginAndDestination(
                    command.originCode(), command.destinationCode(), LocalDateTime.now());
        }
        if (command.departureDate() != null) {
            return flightRepositoryPort.findByDepartureDate(command.departureDate(), LocalDateTime.now());
        }
        return List.of();

    }

    @Override
    @Transactional
    public void reserveSeatsUseCase(Long flightId, int seats) {
        SeatInventory inventory = flightRepositoryPort.findSeatInventoryForUpdate(flightId);
        inventory.reserve(seats);
        flightRepositoryPort.saveSeatInventory(flightId, inventory);
    }


}