package dakota.software.paymentservice.domain;

import dakota.software.paymentservice.domain.vo.Money;
import dakota.software.paymentservice.domain.vo.PaymentMethod;
import dakota.software.paymentservice.domain.vo.PaymentStatus;

import java.time.LocalDateTime;

/**
 * Agregado raíz del dominio de pagos.
 * Representa el resultado de una transacción procesada.
 * No necesita persistencia — es un modelo de proceso, no de datos.
 */
public class Payment {

    private final String transactionId;
    private final Money amount;
    private final String email;
    private final PaymentMethod method;
    private final PaymentStatus status;
    private final LocalDateTime processedAt;
    private final String providerResponse;

    public Payment(String transactionId, Money amount, String email,
                   PaymentMethod method, PaymentStatus status,
                   LocalDateTime processedAt, String providerResponse) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.email = email;
        this.method = method;
        this.status = status;
        this.processedAt = processedAt;
        this.providerResponse = providerResponse;
    }

    public static Payment success(String transactionId, Money amount, String email,
                                  PaymentMethod method, String providerResponse) {
        return new Payment(transactionId, amount, email, method,
                PaymentStatus.COMPLETED, LocalDateTime.now(), providerResponse);
    }

    public static Payment failed(String transactionId, Money amount, String email,
                                 PaymentMethod method, String reason) {
        return new Payment(transactionId, amount, email, method,
                PaymentStatus.FAILED, LocalDateTime.now(), reason);
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    // Getters

    public String getTransactionId() { return transactionId; }
    public Money getAmount() { return amount; }
    public String getEmail() { return email; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public String getProviderResponse() { return providerResponse; }
}