package dakota.software.paymentservice.application.port.out;

import dakota.software.paymentservice.domain.Payment;

/**
 * Puerto de salida: publicación del resultado del pago.
 * El caso de uso delega aquí con el estado ya decidido por el dominio.
 * Modelo sección 5.3: UN único evento con campo status (APPROVED/DECLINED).
 * La implementación concreta (outbox transaccional) vive en infraestructura.
 */
public interface PaymentEventPublisherPort {

    void paymentProcessed(Payment payment, Long bookingId, String causationId);
}
