package dakota.software.flightservice.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Flight(Long id, String flightNumber, Airport origin, Airport destination, LocalDateTime departureAt,
                     LocalDateTime arrivalAt, BigDecimal price, SeatInventory seatInventory) {

    // llamamos a las reglas de negocio para manejar  la reserva de asientos y la actualizacion si hay fallos

    public void reserveSeats(int seats) {
        seatInventory.reserve(seats);
    }

    public void releaseSeats(int seats) {
        seatInventory.release(seats);
    }
}