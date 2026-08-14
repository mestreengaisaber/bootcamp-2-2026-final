package dakota.software.flightservice.domain;

public class SeatInventory {

    private final int totalSeats;
    private int availableSeats;

    //construimos a un tamano el del avion
    public SeatInventory(int totalSeats) {
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("Total seats must be greater than 0");
        }

        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    //tenemos reglas de dominio para reservar
    public void reserve(int seats) {
        if (seats <= 0) {
            throw new IllegalArgumentException("Seats must be greater than 0");
        }

        if (availableSeats < seats) {
            throw new IllegalStateException("Not enough available seats");
        }

        availableSeats -= seats;
    }

    //tenemos reglas de dominio para devolver plazas
    public void release(int seats) {
        if (seats <= 0) {
            throw new IllegalArgumentException("Seats must be greater than 0");
        }

        if (availableSeats + seats > totalSeats) {
            throw new IllegalStateException("Cannot release more seats than total seats");
        }

        availableSeats += seats;
    }


}
