package dakota.software.paymentservice.domain.vo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object inmutable para representar dinero en el contexto de pagos.
 * Repetido intencionadamente respecto a product — cada bounded context
 * es dueño de su modelo y evoluciona independientemente.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "El importe no puede ser null");
        Objects.requireNonNull(currency, "La moneda no puede ser null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El importe no puede ser negativo");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("La moneda es obligatoria");
        }
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        if (!currency.equalsIgnoreCase(other.currency)) {
            throw new IllegalArgumentException("No se pueden sumar monedas distintas");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }
}