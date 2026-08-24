package dakota.software.bookingservice.domain;

/**
 * Vocabulario propio de booking-service para el método de pago elegido por el
 * pasajero. NO es la clase de payment-service: en microservicios cada servicio
 * posee su copia del contrato (los valores coinciden con PaymentMethod de
 * payment y viajan como String en BookingCreatedEvent, sección 6.2 del doc de
 * arquitectura). Compartir la clase Java acoplaría los despliegues.
 */
public enum PaymentMethod {
    STRIPE,
    PAYPAL,
    MOCK;

    public static PaymentMethod fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PaymentMethod must not be null");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown payment method '" + value + "'. Allowed values: STRIPE, PAYPAL, MOCK");
        }
    }
}
