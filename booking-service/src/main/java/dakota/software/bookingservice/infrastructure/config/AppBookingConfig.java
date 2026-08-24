package dakota.software.bookingservice.infrastructure.config;

import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.application.port.out.BookingEventPublisherPort;
import dakota.software.bookingservice.application.port.out.BookingRepositoryPort;
import dakota.software.bookingservice.application.service.BookingService;
import dakota.software.bookingservice.infrastructure.adapter.BookingEventOutboxAdapter;
import dakota.software.bookingservice.infrastructure.outbox.OutboxRelay;
import dakota.software.bookingservice.infrastructure.persistence.BookingJpaRepository;
import dakota.software.bookingservice.infrastructure.persistence.BookingPersistenceAdapter;
import dakota.software.bookingservice.infrastructure.persistence.OutboxJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

@EnableKafka
@Configuration
public class AppBookingConfig {

    @Bean
    public BookingRepositoryPort bookingRepositoryPort(BookingJpaRepository bookingJpaRepository) {
        return new BookingPersistenceAdapter(bookingJpaRepository);
    }

    @Bean
    public BookingEventPublisherPort bookingEventPublisherPort(
            OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        return new BookingEventOutboxAdapter(outboxRepository, objectMapper);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxJpaRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Bean
    public BookingUsecase bookingUsecase(BookingRepositoryPort bookingRepositoryPort,
                                         BookingEventPublisherPort bookingEventPublisherPort) {
        return new BookingService(bookingRepositoryPort, bookingEventPublisherPort);
    }
}