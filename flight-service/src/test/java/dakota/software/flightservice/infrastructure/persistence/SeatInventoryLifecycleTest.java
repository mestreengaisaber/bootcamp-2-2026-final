package dakota.software.flightservice.infrastructure.persistence;

import dakota.software.flightservice.application.exception.SeatInventoryNotFoundException;
import dakota.software.flightservice.domain.SeatInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(FlightPersistenceAdapter.class)
class SeatInventoryLifecycleTest {

    @Autowired
    private FlightPersistenceAdapter adapter;

    @Autowired
    private AirportJpaRepository airports;

    @Autowired
    private FlightJpaRepository flights;

    @Autowired
    private SeatInventoryJpaRepository seatInventory;

    private Long flightId;

    @BeforeEach
    void setUp() {
        seatInventory.deleteAllInBatch();
        flights.deleteAllInBatch();
        airports.deleteAllInBatch();
        AirportEntity mad = airports.save(new AirportEntity("MAD", "Adolfo Suarez", "Madrid", "Spain"));
        AirportEntity bcn = airports.save(new AirportEntity("BCN", "El Prat", "Barcelona", "Spain"));
        FlightEntity flight = new FlightEntity("IB1", mad, bcn,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1),
                new BigDecimal("199.90"));
        flight.setSeatInventory(new SeatInventoryEntity(flight, 180, 150));
        flightId = flights.save(flight).getId();
    }

    @Test
    void reserveSeatsAcquiresLockDecrementsAndPersists() {
        SeatInventory inventory = adapter.findSeatInventoryForUpdate(flightId);

        inventory.reserve(5);
        adapter.saveSeatInventory(flightId, inventory);

        SeatInventory reloaded = adapter.findSeatInventoryForUpdate(flightId);
        assertThat(reloaded.getAvailableSeats()).isEqualTo(145);
    }

    @Test
    void reserveMoreThanAvailableFailsCleanlyAndPersistsNothing() {
        SeatInventory inventory = adapter.findSeatInventoryForUpdate(flightId);

        assertThatThrownBy(() -> inventory.reserve(151))
                .isInstanceOf(IllegalStateException.class);

        SeatInventory reloaded = adapter.findSeatInventoryForUpdate(flightId);
        assertThat(reloaded.getAvailableSeats()).isEqualTo(150);
    }

    @Test
    void findSeatInventoryForUpdateThrowsWhenFlightDoesNotExist() {
        assertThatThrownBy(() -> adapter.findSeatInventoryForUpdate(999L))
                .isInstanceOf(SeatInventoryNotFoundException.class);
    }
}