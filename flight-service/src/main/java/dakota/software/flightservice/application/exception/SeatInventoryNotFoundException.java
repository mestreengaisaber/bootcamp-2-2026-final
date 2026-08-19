package dakota.software.flightservice.application.exception;

public class SeatInventoryNotFoundException extends RuntimeException {

    public SeatInventoryNotFoundException(Long flightId) {
        super("Seat inventory not found with id: " + flightId);
    }
}