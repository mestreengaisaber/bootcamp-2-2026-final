package dakota.software.paymentservice.infraestructure.config;

import dakota.software.paymentservice.application.port.in.ProcessPaymentUseCase;
import dakota.software.paymentservice.application.port.out.PaymentEventPublisherPort;
import dakota.software.paymentservice.application.port.out.PaymentGateway;
import dakota.software.paymentservice.application.port.out.PaymentRepositoryPort;
import dakota.software.paymentservice.application.service.ProcessPaymentService;
import dakota.software.paymentservice.infraestructure.adapter.*;
import dakota.software.paymentservice.infraestructure.outbox.OutboxRelay;
import dakota.software.paymentservice.infraestructure.persistence.OutboxJpaRepository;
import dakota.software.paymentservice.infraestructure.persistence.PaymentJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@EnableKafka
@Configuration
public class AppPaymentConfig {

    @Bean
    public PaymentGateway stripePaymentAdapter(@Value("${app.stripe-key}") String apiKey) {
        return new StripePaymentAdapter(apiKey);
    }

    @Bean
    public PaymentGateway paypalPaymentAdapter(@Value("${app.paypal-key}") String apiKey) {
        return new PaypalPaymentAdapter(apiKey);
    }

    @Bean
    public PaymentGateway mockPaymentAdapter(@Value("${app.fail-payments:false}") boolean failPayments) {
        return new MockPaymentAdapter(failPayments);
    }

    @Bean
    public PaymentRepositoryPort paymentRepositoryPort(PaymentJpaRepository paymentJpaRepository) {
        return new JpaPaymentAdapter(paymentJpaRepository);
    }

    @Bean
    public PaymentEventPublisherPort paymentEventPublisherPort(
            OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        return new PaymentEventOutboxAdapter(outboxRepository, objectMapper);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxJpaRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Bean
    public ProcessPaymentUseCase processPaymentUseCase(List<PaymentGateway> gateways,
                                                       PaymentRepositoryPort paymentRepository,
                                                       PaymentEventPublisherPort eventPublisherPort) {
        return new ProcessPaymentService(gateways, paymentRepository, eventPublisherPort);
    }
}
