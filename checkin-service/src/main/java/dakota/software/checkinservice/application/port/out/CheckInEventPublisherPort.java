package dakota.software.checkinservice.application.port.out;

import dakota.software.checkinservice.domain.CheckIn;

public interface CheckInEventPublisherPort {
    //definimos la relacion del evento con el outbox
    void checkInCompleted(CheckIn checkIn);
}
