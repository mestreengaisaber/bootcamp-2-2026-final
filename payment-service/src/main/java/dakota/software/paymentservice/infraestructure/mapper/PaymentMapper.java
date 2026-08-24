package dakota.software.paymentservice.infraestructure.mapper;

import dakota.software.paymentservice.infraestructure.persistence.PaymentEntity;
import dakota.software.paymentservice.domain.Payment;
import dakota.software.paymentservice.domain.vo.Money;
import dakota.software.paymentservice.domain.vo.PaymentMethod;
import dakota.software.paymentservice.domain.vo.PaymentStatus;

public class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
                null,
                payment.getTransactionId(),
                payment.getAmount().amount(),
                payment.getAmount().currency(),
                payment.getEmail(),
                payment.getMethod().name(),
                payment.getStatus().name(),
                payment.getProcessedAt(),
                payment.getProviderResponse()
        );
    }

    public static Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getTransactionId(),
                new Money(entity.getAmount(), entity.getCurrency()),
                entity.getEmail(),
                PaymentMethod.valueOf(entity.getMethod()),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getProcessedAt(),
                entity.getProviderResponse()
        );
    }
}