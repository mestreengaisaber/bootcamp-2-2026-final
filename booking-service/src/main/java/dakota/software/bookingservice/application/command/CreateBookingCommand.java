package dakota.software.bookingservice.application.command;

import dakota.software.bookingservice.domain.PaymentMethod;

import java.math.BigDecimal;

public record CreateBookingCommand(String passengerId, Long flightId, int seats,
                                   BigDecimal amount, String passengerName, String passengerEmail,
                                   PaymentMethod paymentMethod) {
}