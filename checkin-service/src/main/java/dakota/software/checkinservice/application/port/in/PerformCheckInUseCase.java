package dakota.software.checkinservice.application.port.in;

import dakota.software.checkinservice.application.command.PerformCheckInCommand;
import dakota.software.checkinservice.domain.CheckIn;

public interface PerformCheckInUseCase {
    CheckIn performCheckIn(PerformCheckInCommand command);
}
