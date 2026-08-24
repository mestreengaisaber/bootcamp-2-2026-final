package dakota.software.paymentservice.application.port.out;

import dakota.software.paymentservice.domain.Payment;

/**
 * Puerto de salida: persistencia de pagos.
 * El caso de uso guarda el resultado del pago a través de esta interfaz.
 * La implementación concreta (JPA, mock, etc.) vive en infraestructura.
 */
public interface PaymentRepositoryPort {
    void save(Payment payment);
}
