package dakota.software.checkinservice.infrastructure.config;

import dakota.software.checkinservice.application.port.in.PerformCheckInUseCase;
import dakota.software.checkinservice.application.port.out.BookingValidationPort;
import dakota.software.checkinservice.application.port.out.CheckInEventPublisherPort;
import dakota.software.checkinservice.application.port.out.CheckInRepositoryPort;
import dakota.software.checkinservice.application.service.CheckInService;
import dakota.software.checkinservice.infrastructure.adapter.CheckInEventOutboxAdapter;
import dakota.software.checkinservice.infrastructure.outbox.OutboxRelay;
import dakota.software.checkinservice.infrastructure.persistence.BookingValidationAdapter;
import dakota.software.checkinservice.infrastructure.persistence.CheckInJpaRepository;
import dakota.software.checkinservice.infrastructure.persistence.CheckInPersistenceAdapter;
import dakota.software.checkinservice.infrastructure.persistence.OutboxJpaRepository;
import dakota.software.checkinservice.infrastructure.persistence.ProcessedEventJpaRepository;
import dakota.software.checkinservice.infrastructure.web.CheckInController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

@EnableKafka
@Configuration
public class AppCheckInConfig {

    @Bean
    public CheckInRepositoryPort checkInRepositoryPort(CheckInJpaRepository checkInJpaRepository) {
        return new CheckInPersistenceAdapter(checkInJpaRepository);
    }

    @Bean
    public CheckInEventPublisherPort checkInEventPublisherPort(OutboxJpaRepository outboxRepository,
                                                                ObjectMapper objectMapper) {
        return new CheckInEventOutboxAdapter(outboxRepository, objectMapper);
    }

    @Bean
    public BookingValidationPort bookingValidationPort(ProcessedEventJpaRepository processedEventRepository) {
        return new BookingValidationAdapter(processedEventRepository);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxJpaRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Bean
    public PerformCheckInUseCase performCheckInUseCase(CheckInRepositoryPort checkInRepositoryPort,
                                                        CheckInEventPublisherPort checkInEventPublisherPort,
                                                        BookingValidationPort bookingValidationPort) {
        return new CheckInService(checkInRepositoryPort, checkInEventPublisherPort, bookingValidationPort);
    }

    @Bean
    public CheckInController checkInController(PerformCheckInUseCase performCheckInUseCase) {
        return new CheckInController(performCheckInUseCase);
    }
}
