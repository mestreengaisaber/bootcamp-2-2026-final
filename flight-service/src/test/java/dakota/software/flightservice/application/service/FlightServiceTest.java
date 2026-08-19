package dakota.software.flightservice.application.service;

import dakota.software.flightservice.application.command.SearchCommand;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.domain.Airport;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.domain.SeatInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class FlightServiceTest {


    private static final Flight FLIGHT = new Flight(
            1L,
            "IB1234",
            new Airport(1L, "MAD", "Adolfo Suarez", "Madrid", "Spain"),
            new Airport(2L, "BCN", "El Prat", "Barcelona", "Spain"),
            LocalDateTime.of(2026, 8, 20, 10, 0),
            LocalDateTime.of(2026, 8, 20, 11, 30),
            new BigDecimal("199.90"),
            new SeatInventory(180, 150));

    private final FakeFlightRepository repository = new FakeFlightRepository();
    private final FlightService service = new FlightService(repository);

    @BeforeEach
    void setUp() {
        repository.reset();
    }

    @Test
    void searchByDateUseCaseDelegatesToRepository() {
        SearchCommand command = new SearchCommand(null, null, LocalDate.of(2026, 8, 20));

        List<Flight> result = service.searchByDateUseCase(command);

        assertEquals(1, result.size());
        assertEquals("IB1234", result.get(0).flightNumber());
    }

    @Test
    void searchByOriginDestinationUseCaseDelegatesToRepository() {
        SearchCommand command = new SearchCommand("MAD", "BCN", null);

        List<Flight> result = service.searchByOriginDestinationUseCase(command);

        assertEquals(1, result.size());
        assertEquals("IB1234", result.get(0).flightNumber());
    }

    @Test
    void reserveSeatsUseCaseDecrementsAvailableSeats() {
        service.reserveSeatsUseCase(1L, 5);

        assertEquals(145, repository.storedInventory.getAvailableSeats());
    }

    @Test
    void reserveSeatsUseCaseThrowsWhenNotEnoughSeats() {
        assertThrows(IllegalStateException.class, () -> service.reserveSeatsUseCase(1L, 151));

        assertEquals(150, repository.storedInventory.getAvailableSeats());
    }

    private static final class FakeFlightRepository implements FlightRepositoryPort {

        private SeatInventory storedInventory;

        void reset() {
            storedInventory = new SeatInventory(180, 150);
        }

        @Override
        public List<Flight> findByDepartureDate(LocalDate date, LocalDateTime from) {
            return List.of(FLIGHT);
        }

        @Override
        public List<Flight> findByOriginAndDestination(String originCode, String destinationCode, LocalDateTime from) {
            return List.of(FLIGHT);
        }

        @Override
        public SeatInventory findSeatInventoryForUpdate(Long flightId) {
            return storedInventory;
        }

        @Override
        public void saveSeatInventory(Long flightId, SeatInventory seatInventory) {
            storedInventory = seatInventory;
        }
    }
}