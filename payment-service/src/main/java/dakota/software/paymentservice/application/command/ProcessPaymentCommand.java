package dakota.software.paymentservice.application.command;

import dakota.software.paymentservice.domain.vo.PaymentMethod;

import java.math.BigDecimal;

/**
 * Comando de entrada para procesar un pago.
 * Objeto plano sin comportamientos — solo transporta datos al caso de uso.
 * causationId: eventId del evento que causó el pago (null cuando entra por REST).
 */
public record ProcessPaymentCommand(
        Long bookingId,
        BigDecimal amount,
        String currency,
        String email,
        PaymentMethod method,
        String causationId
) {}
