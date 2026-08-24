package dakota.software.paymentservice.infraestructure.adapter;

import dakota.software.paymentservice.application.port.out.PaymentGateway;
import dakota.software.paymentservice.domain.Payment;
import dakota.software.paymentservice.domain.vo.Money;
import dakota.software.paymentservice.domain.vo.PaymentMethod;

import java.util.UUID;
import java.util.logging.Logger;

public class MockPaymentAdapter implements PaymentGateway {

    private final Logger log = Logger.getLogger(MockPaymentAdapter.class.getName());

    private final boolean failPayments;

    public MockPaymentAdapter(boolean failPayments) {
        this.failPayments = failPayments;
    }

    @Override
    public Payment charge(Money amount, String email, PaymentMethod method) {
        log.info("Procesando pago MOCK de " + amount.amount() + " " + amount.currency());

        // Hook de pruebas: con app.fail-payments=true este gateway devuelve FAILED,
        // activando la rama de compensación de la SAGA (PaymentFailed -> StockReleased).
        if (failPayments) {
            return Payment.failed(
                    UUID.randomUUID().toString(),
                    amount,
                    email,
                    method,
                    "Pago simulado FALLIDO (app.fail-payments=true)"
            );
        }

        return Payment.success(
                UUID.randomUUID().toString(),
                amount,
                email,
                method,
                "Pago simulado correctamente (MOCK)"
        );
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.MOCK;
    }
}