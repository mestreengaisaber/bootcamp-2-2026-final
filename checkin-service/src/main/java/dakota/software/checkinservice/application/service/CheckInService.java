package dakota.software.checkinservice.application.service;

import dakota.software.checkinservice.application.command.PerformCheckInCommand;
import dakota.software.checkinservice.application.exception.BookingNotConfirmedException;
import dakota.software.checkinservice.application.exception.CheckInAlreadyExistsException;
import dakota.software.checkinservice.application.port.in.PerformCheckInUseCase;
import dakota.software.checkinservice.application.port.out.CheckInEventPublisherPort;
import dakota.software.checkinservice.application.port.out.CheckInRepositoryPort;
import dakota.software.checkinservice.application.port.out.BookingValidationPort;
import dakota.software.checkinservice.domain.BoardingPass;
import dakota.software.checkinservice.domain.CheckIn;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public class CheckInService implements PerformCheckInUseCase {

    private final CheckInRepositoryPort checkInRepositoryPort;
    private final CheckInEventPublisherPort checkInEventPublisherPort;
    private final BookingValidationPort bookingValidationPort;

    public CheckInService(CheckInRepositoryPort checkInRepositoryPort,
                          CheckInEventPublisherPort checkInEventPublisherPort,
                          BookingValidationPort bookingValidationPort) {
        this.checkInRepositoryPort = checkInRepositoryPort;
        this.checkInEventPublisherPort = checkInEventPublisherPort;
        this.bookingValidationPort = bookingValidationPort;
    }

    @Override
    @Transactional
    public CheckIn performCheckIn(PerformCheckInCommand command) {

        if (checkInRepositoryPort.findByBookingId(command.bookingId()).isPresent()) {
            throw new CheckInAlreadyExistsException(command.bookingId());
        }

        // Caso de uso: No se puede hacer check-in sin reserva confirmada.
        if (!bookingValidationPort.existsConfirmedBooking(command.bookingId())) {
            throw new BookingNotConfirmedException(command.bookingId());
        }

        //Crea CheckIn
        CheckIn checkIn = CheckIn.create(command.bookingId(), command.flightId(), command.passengerId());
        //Crea la tarjeta embarque
        BoardingPass boardingPass = generateBoardingPass(checkIn);
        //lo da por completado cambiando el estado
        checkIn.complete(boardingPass);

        //guarda el objeto
        CheckIn saved = checkInRepositoryPort.save(checkIn);
        //publica el evento
        checkInEventPublisherPort.checkInCompleted(saved);

        return saved;
    }

    //boardingtime se tendra que pensar los 45 minutos o lo que sea .
    private BoardingPass generateBoardingPass(CheckIn checkIn) {
        String seat = "S" + checkIn.getId();    // "S1", "S2"... determinista
        String gate = "G" + checkIn.getFlightId(); // "G5", "G8"... determinista
        return new BoardingPass(
                null,
                checkIn.getId(),
                seat,
                gate,
                LocalDateTime.now().plusMinutes(45)
        );
    }
}
