package dakota.software.bookingservice.infrastructure.config;

import dakota.software.bookingservice.application.port.in.BookingUsecase;
import dakota.software.bookingservice.application.port.out.BookingRepositoryPort;
import dakota.software.bookingservice.application.service.BookingService;
import dakota.software.bookingservice.infrastructure.persistence.BookingJpaRepository;
import dakota.software.bookingservice.infrastructure.persistence.BookingPersistenceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBookingConfig {

    @Bean
    public BookingRepositoryPort bookingRepositoryPort(BookingJpaRepository bookingJpaRepository) {
        return new BookingPersistenceAdapter(bookingJpaRepository);
    }

    @Bean
    public BookingUsecase bookingUsecase(BookingRepositoryPort bookingRepositoryPort) {
        return new BookingService(bookingRepositoryPort);
    }
}