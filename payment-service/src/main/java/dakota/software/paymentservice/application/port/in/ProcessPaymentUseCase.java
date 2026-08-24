package dakota.software.paymentservice.application.port.in;

import dakota.software.paymentservice.application.command.ProcessPaymentCommand;
import dakota.software.paymentservice.domain.Payment;

/**
 * Puerto de entrada: caso de uso para procesar un pago.
 * El orquestador (controller, cola, etc.) depende de esta interfaz,
 * no de la implementación concreta.
 */
public interface ProcessPaymentUseCase {
    Payment process(ProcessPaymentCommand command);
}