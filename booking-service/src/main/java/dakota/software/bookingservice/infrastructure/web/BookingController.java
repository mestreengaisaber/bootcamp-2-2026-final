package dakota.software.bookingservice.infrastructure.web;

import dakota.software.bookingservice.application.command.CreateBookingCommand;
import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.domain.Booking;
import dakota.software.bookingservice.domain.PaymentMethod;
import dakota.software.bookingservice.infrastructure.web.dto.BookingResponse;
import dakota.software.bookingservice.infrastructure.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingUsecase bookingUsecase;

    public BookingController(BookingUsecase bookingUsecase) {
        this.bookingUsecase = bookingUsecase;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {

        // El String del transporte se traduce a enum del dominio aquí (borde web).
        // Un valor no reconocido es un error del cliente: 400, no 500.
        PaymentMethod paymentMethod;
        try {
            paymentMethod = PaymentMethod.fromString(request.paymentMethod());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        CreateBookingCommand command = new CreateBookingCommand(
                userId,
                request.flightId(),
                request.seats(),
                request.amount(),
                request.passengerName(),
                request.passengerEmail(),
                paymentMethod);

        Booking booking = bookingUsecase.createBookingUseCase(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return BookingResponse.from(bookingUsecase.getBookingById(id));
    }
}