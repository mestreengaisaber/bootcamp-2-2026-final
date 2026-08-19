package dakota.software.flightservice.infrastructure.persistence;

import dakota.software.flightservice.domain.Flight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(FlightPersistenceAdapter.class)
class FlightPersistenceSliceTest {

    @Autowired
    private FlightPersistenceAdapter adapter;

    @Autowired
    private AirportJpaRepository airports;

    @Autowired
    private FlightJpaRepository flights;

    @Autowired
    private SeatInventoryJpaRepository seatInventory;

    private AirportEntity mad;
    private AirportEntity bcn;

    @BeforeEach
    void setUp() {
        seatInventory.deleteAllInBatch();
        flights.deleteAllInBatch();
        airports.deleteAllInBatch();
        mad = airports.save(new AirportEntity("MAD", "Adolfo Suarez", "Madrid", "Spain"));
        bcn = airports.save(new AirportEntity("BCN", "El Prat", "Barcelona", "Spain"));
    }

    @Test
    void findByDepartureDateUsesHalfOpenRange() {
        LocalDate day = LocalDate.of(2026, 8, 20);
        saveFlight("IB1", day.atStartOfDay());
        saveFlight("IB2", day.atTime(23, 59));
        saveFlight("IB3", day.plusDays(1).atStartOfDay());

        List<Flight> result = adapter.findByDepartureDate(day, day.atStartOfDay().minusHours(1));

        assertThat(result).extracting(Flight::flightNumber)
                .containsExactlyInAnyOrder("IB1", "IB2");
    }

    @Test
    void findByDepartureDateHonoursFromBoundary() {
        LocalDate day = LocalDate.of(2026, 8, 20);
        saveFlight("IB1", day.atStartOfDay());
        saveFlight("IB2", day.atTime(8, 0));

        List<Flight> result = adapter.findByDepartureDate(day, day.atTime(7, 0));

        assertThat(result).extracting(Flight::flightNumber).containsExactly("IB2");
    }

    @Test
    void findByOriginAndDestinationOnlyReturnsFutureFlights() {
        saveFlight("IB1", LocalDateTime.now().minusHours(1));
        saveFlight("IB2", LocalDateTime.now().plusDays(1));

        List<Flight> result = adapter.findByOriginAndDestination("MAD", "BCN", LocalDateTime.now());

        assertThat(result).extracting(Flight::flightNumber).containsExactly("IB2");
    }

    @Test
    void loadsAssociationsWithoutLazyInitializationException() {
        saveFlight("IB1", LocalDateTime.now().plusDays(1));

        List<Flight> result = adapter.findByOriginAndDestination("MAD", "BCN", LocalDateTime.now());

        assertThat(result).singleElement().satisfies(flight -> {
            assertThat(flight.origin().code()).isEqualTo("MAD");
            assertThat(flight.destination().city()).isEqualTo("Barcelona");
            assertThat(flight.seatInventory().getAvailableSeats()).isEqualTo(150);
        });
    }

    private void saveFlight(String flightNumber, LocalDateTime departureAt) {
        FlightEntity entity = new FlightEntity(
                flightNumber, mad, bcn, departureAt, departureAt.plusHours(1), new BigDecimal("199.90"));
        entity.setSeatInventory(new SeatInventoryEntity(entity, 180, 150));
        flights.save(entity);
    }
}