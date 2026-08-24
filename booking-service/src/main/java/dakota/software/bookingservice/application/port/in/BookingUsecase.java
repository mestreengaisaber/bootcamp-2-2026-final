package dakota.software.bookingservice.application.port.in;

import dakota.software.bookingservice.application.command.CreateBookingCommand;
import dakota.software.bookingservice.domain.Booking;

public interface BookingUsecase {
    Booking createBookingUseCase(CreateBookingCommand  createBookingCommand);
    Booking getBookingById(Long id);
    Booking applyPaymentResult(Long bookingId, String status, String reason, String causationId);
}
