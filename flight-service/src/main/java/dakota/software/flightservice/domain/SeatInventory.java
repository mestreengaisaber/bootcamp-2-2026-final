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

    // reconstruccion desde persistencia (el adapter restaura el estado real)
    public SeatInventory(int totalSeats, int availableSeats) {
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("Total seats must be greater than 0");
        }

        if (availableSeats < 0 || availableSeats > totalSeats) {
            throw new IllegalArgumentException("Available seats must be between 0 and total seats");
        }

        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
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