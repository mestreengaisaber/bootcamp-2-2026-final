package dakota.software.bookingservice.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateBookingRequest(
        @NotNull Long flightId,
        @NotNull @Positive Integer seats,
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        @NotBlank String passengerName,
        @NotBlank String passengerEmail,
        @NotBlank String paymentMethod) {
}