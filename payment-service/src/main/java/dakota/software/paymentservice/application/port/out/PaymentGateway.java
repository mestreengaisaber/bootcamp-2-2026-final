package dakota.software.paymentservice.application.port.out;

import dakota.software.paymentservice.domain.Payment;
import dakota.software.paymentservice.domain.vo.Money;
import dakota.software.paymentservice.domain.vo.PaymentMethod;

/**
 * Puerto de salida: abstracción sobre el proveedor de pagos externo.
 * Stripe, PayPal, o cualquier otro implementan esta interfaz.
 * El caso de uso nunca depende de un proveedor concreto.
 */
public interface PaymentGateway {
    Payment charge(Money amount, String email, PaymentMethod method);

    boolean supports(PaymentMethod method);
}