package dakota.software.flightservice.application.service;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.in.GetFlightsUseCase;
import dakota.software.flightservice.application.port.in.ReleaseSeatsUseCase;
import dakota.software.flightservice.application.port.in.ReserveSeatsUseCase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.domain.SeatInventory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


public class FlightService implements GetFlightsUseCase, ReserveSeatsUseCase, ReleaseSeatsUseCase {

    private final FlightRepositoryPort flightRepositoryPort;

    public FlightService(FlightRepositoryPort flightRepositoryPort) {
        this.flightRepositoryPort = flightRepositoryPort;
    }

    @Override
    public List<Flight> searchByDate(SearchCommand command) {
        return flightRepositoryPort.findByDepartureDate(command.departureDate(), LocalDateTime.now());
    }

    @Override
    public List<Flight> searchByOriginDestination(SearchCommand command) {
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
    public void reserve(Long flightId, int seats) {
        SeatInventory inventory = flightRepositoryPort.findSeatInventoryForUpdate(flightId);
        //utilizamos los metodos del dominio
        inventory.reserve(seats);
        flightRepositoryPort.saveSeatInventory(flightId, inventory);
    }

    @Override
    @Transactional
    public void release(Long flightId, int seats) {
        SeatInventory inventory = flightRepositoryPort.findSeatInventoryForUpdate(flightId);
        //utilizamos los metodos del dominio
        inventory.release(seats);
        flightRepositoryPort.saveSeatInventory(flightId, inventory);

    }
}