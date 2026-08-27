package dakota.software.checkinservice.application.exception;

public class CheckInAlreadyExistsException extends RuntimeException {
    public CheckInAlreadyExistsException(Long bookingId) {
        super("Check-in already exists for booking: " + bookingId);
    }
}
