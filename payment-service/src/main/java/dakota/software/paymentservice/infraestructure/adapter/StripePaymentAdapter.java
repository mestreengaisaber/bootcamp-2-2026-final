package dakota.software.paymentservice.infraestructure.adapter;

import dakota.software.paymentservice.application.port.out.PaymentGateway;
import dakota.software.paymentservice.domain.Payment;
import dakota.software.paymentservice.domain.vo.Money;
import dakota.software.paymentservice.domain.vo.PaymentMethod;

import java.util.UUID;
import java.util.logging.Logger;

public class StripePaymentAdapter implements PaymentGateway {

    private final String apiKey;
    private final Logger log = Logger.getLogger(StripePaymentAdapter.class.getName());

    public StripePaymentAdapter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Payment charge(Money amount, String email, PaymentMethod method) {
        log.info("Conectando a Stripe API con clave: " + apiKey);
        log.info("Procesando pago de " + amount.amount() + " " + amount.currency()
                + " para " + email);

        // Simulación de llamada a Stripe
        return Payment.success(
                UUID.randomUUID().toString(),
                amount,
                email,
                method,
                "Pago procesado correctamente vía Stripe"
        );
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.STRIPE;
    }
}