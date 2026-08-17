package dakota.software.bookingservice.application.command;

import java.math.BigDecimal;

public record CreateBookingCommand(String passengerId, Long flightId, int seats,
                                   BigDecimal amount, String passengerName, String passengerEmail) {
}