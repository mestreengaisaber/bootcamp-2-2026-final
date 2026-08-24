package dakota.software.paymentservice.application.service;

import dakota.software.paymentservice.application.command.ProcessPaymentCommand;
import dakota.software.paymentservice.application.port.in.ProcessPaymentUseCase;
import dakota.software.paymentservice.application.port.out.PaymentEventPublisherPort;
import dakota.software.paymentservice.application.port.out.PaymentGateway;
import dakota.software.paymentservice.application.port.out.PaymentRepositoryPort;
import dakota.software.paymentservice.domain.Payment;
import dakota.software.paymentservice.domain.exception.PaymentProcessingException;
import dakota.software.paymentservice.domain.vo.Money;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: procesar un pago.
 * Orquesta el flujo: validar comando → crear objeto dinero → delegar en gateway → persistir resultado → publicar evento → devolver.
 * El pago y su evento de resultado se persisten en la MISMA transacción (outbox transaccional).
 * No sabe qué gateway concreto hay detrás ni cómo se persiste.
 */
@Transactional
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final List<PaymentGateway> gateways;
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentEventPublisherPort eventPublisher;

    public ProcessPaymentService(List<PaymentGateway> gateways, PaymentRepositoryPort paymentRepository,
                                 PaymentEventPublisherPort eventPublisher) {
        this.gateways = gateways;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Payment process(ProcessPaymentCommand command) {
        var money = new Money(command.amount(), command.currency());

        var gateway = gateways.stream()
                .filter(g -> g.supports(command.method()))
                .findFirst()
                .orElseThrow(() -> new PaymentProcessingException(
                        "No hay gateway disponible para: " + command.method()));

        Payment payment = gateway.charge(money, command.email(), command.method());

        paymentRepository.save(payment);

        // Persistir el evento de resultado en el outbox, en la misma transacción que el pago.
        // El OutboxRelay lo publicará a Kafka posteriormente.
        eventPublisher.paymentProcessed(payment, command.bookingId(), command.causationId());

        return payment;
    }
}
