package dakota.software.paymentservice.infraestructure.adapter;

import dakota.software.paymentservice.application.port.out.PaymentRepositoryPort;
import dakota.software.paymentservice.infraestructure.mapper.PaymentMapper;
import dakota.software.paymentservice.infraestructure.persistence.PaymentJpaRepository;
import dakota.software.paymentservice.domain.Payment;

public class JpaPaymentAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository paymentJpaRepository;

    public JpaPaymentAdapter(PaymentJpaRepository paymentJpaRepository) {
        this.paymentJpaRepository = paymentJpaRepository;
    }

    @Override
    public void save(Payment payment) {
        var entity = PaymentMapper.toEntity(payment);
        paymentJpaRepository.save(entity);
    }
}
