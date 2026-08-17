package dakota.software.bookingservice.infrastructure.web;

import dakota.software.bookingservice.application.command.CreateBookingCommand;
import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.infrastructure.web.dto.BookingResponse;
import dakota.software.bookingservice.infrastructure.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingUsecase bookingUsecase;

    public BookingController(BookingUsecase bookingUsecase) {
        this.bookingUsecase = bookingUsecase;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication) {

        String passengerId = authentication.getName();

        CreateBookingCommand command = new CreateBookingCommand(
                passengerId,
                request.flightId(),
                request.seats(),
                request.amount(),
                request.passengerName(),
                request.passengerEmail());

        Booking booking = bookingUsecase.createBookingUseCase(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return BookingResponse.from(bookingUsecase.getBookingById(id));
    }
}