package dakota.software.checkinservice.application.port.out;

import dakota.software.checkinservice.domain.CheckIn;

import java.util.Optional;

public interface CheckInRepositoryPort {
    CheckIn save(CheckIn checkIn);
    //actua como orquestrador sobre la regla de negocio accede a la perssitencia sea la que sea definida
    // un pasajero solo puede facturar una vez por reserva
    Optional<CheckIn> findByBookingId(Long bookingId);

}
