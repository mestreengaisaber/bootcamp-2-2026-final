package dakota.software.flightservice.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Flight {

    private Long id;
    private String flightNumber;

    private Airport origin;
    private Airport destination;

    private LocalDateTime departureAt;
    private LocalDateTime arrivalAt;

    private BigDecimal price;

    private SeatInventory seatInventory;


    public Flight(
            Long id,
            String flightNumber,
            Airport origin,
            Airport destination,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            BigDecimal price,
            SeatInventory seatInventory) {

        this.id = id;
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.price = price;
        this.seatInventory = seatInventory;
    }


    public Long getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public Airport getOrigin() {
        return origin;
    }

    public Airport getDestination() {
        return destination;
    }

    public LocalDateTime getDepartureAt() {
        return departureAt;
    }

    public LocalDateTime getArrivalAt() {
        return arrivalAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public SeatInventory getSeatInventory() {
        return seatInventory;
    }

    // llamamos a las reglas de negocio para manejar  la reserva de asientos y la actualizacion si hay fallos

    public void reserveSeats(int seats) {
        seatInventory.reserve(seats);
    }

    public void releaseSeats(int seats) {
        seatInventory.release(seats);
    }
}