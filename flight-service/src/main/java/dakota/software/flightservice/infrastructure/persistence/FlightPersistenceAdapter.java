package dakota.software.flightservice.infrastructure.persistence;

import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.application.exception.SeatInventoryNotFoundException;
import dakota.software.flightservice.domain.Airport;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.domain.SeatInventory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


public class FlightPersistenceAdapter implements FlightRepositoryPort {

    private final FlightJpaRepository flightJpaRepository;
    private final SeatInventoryJpaRepository seatInventoryJpaRepository;

    public FlightPersistenceAdapter(FlightJpaRepository flightJpaRepository,
                                    SeatInventoryJpaRepository seatInventoryJpaRepository) {
        this.flightJpaRepository = flightJpaRepository;
        this.seatInventoryJpaRepository = seatInventoryJpaRepository;
    }

    @Override
    public List<Flight> findByDepartureDate(LocalDate date, LocalDateTime from) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime start = startOfDay.isBefore(from) ? from : startOfDay;
        LocalDateTime end = startOfDay.plusDays(1);
        return flightJpaRepository.findByDepartureAtRange(start, end).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Flight> findByOriginAndDestination(String originCode, String destinationCode, LocalDateTime from) {
        return flightJpaRepository.findByOriginAndDestinationAfter(originCode, destinationCode, from).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public SeatInventory findSeatInventoryForUpdate(Long flightId) {
        SeatInventoryEntity entity = seatInventoryJpaRepository.findByFlightIdWithLock(flightId)
                .orElseThrow(() -> new SeatInventoryNotFoundException(flightId));
        return toSeatInventory(entity);
    }

    @Override
    public void saveSeatInventory(Long flightId, SeatInventory seatInventory) {
        SeatInventoryEntity entity = seatInventoryJpaRepository.findByFlightId(flightId)
                .orElseThrow(() -> new SeatInventoryNotFoundException(flightId));
        entity.updateAvailableSeats(seatInventory.getAvailableSeats());
    }


    //mapper

    private Flight toDomain(FlightEntity entity) {
        Airport origin = toAirport(entity.getOrigin());
        Airport destination = toAirport(entity.getDestination());
        SeatInventory seatInventory = new SeatInventory(
                entity.getSeatInventory().getTotalSeats(),
                entity.getSeatInventory().getAvailableSeats());
        return new Flight(
                entity.getId(),
                entity.getFlightNumber(),
                origin,
                destination,
                entity.getDepartureAt(),
                entity.getArrivalAt(),
                entity.getPrice(),
                seatInventory);
    }

    private Airport toAirport(AirportEntity entity) {
        return new Airport(entity.getId(), entity.getCode(), entity.getName(), entity.getCity(), entity.getCountry());
    }

    private SeatInventory toSeatInventory(SeatInventoryEntity entity) {
        return new SeatInventory(entity.getTotalSeats(), entity.getAvailableSeats());
    }
}