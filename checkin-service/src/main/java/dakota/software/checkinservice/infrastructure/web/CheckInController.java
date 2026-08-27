package dakota.software.checkinservice.infrastructure.web;

import dakota.software.checkinservice.application.command.PerformCheckInCommand;
import dakota.software.checkinservice.application.exception.BookingNotConfirmedException;
import dakota.software.checkinservice.application.exception.CheckInAlreadyExistsException;
import dakota.software.checkinservice.application.port.in.PerformCheckInUseCase;
import dakota.software.checkinservice.domain.CheckIn;
import dakota.software.checkinservice.infrastructure.web.dto.CheckInResponse;
import dakota.software.checkinservice.infrastructure.web.dto.PerformCheckInRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkins")
public class CheckInController {

    private final PerformCheckInUseCase performCheckInUseCase;

    public CheckInController(PerformCheckInUseCase performCheckInUseCase) {
        this.performCheckInUseCase = performCheckInUseCase;
    }

    @PostMapping
    public ResponseEntity<?> performCheckIn(@Valid @RequestBody PerformCheckInRequest request) {
        try {
            PerformCheckInCommand command = new PerformCheckInCommand(
                    request.bookingId(),
                    request.flightId(),
                    request.passengerId());

            CheckIn checkIn = performCheckInUseCase.performCheckIn(command);
            return ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(checkIn));

        } catch (CheckInAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (BookingNotConfirmedException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
