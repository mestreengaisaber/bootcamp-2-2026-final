package dakota.software.flightservice.infrastructure.config;

import dakota.software.flightservice.application.port.in.GetFlightsUseCase;
import dakota.software.flightservice.application.port.in.ReleaseSeatsUseCase;
import dakota.software.flightservice.application.port.in.ReserveSeatsUseCase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.application.service.FlightService;
import dakota.software.flightservice.infrastructure.kafka.BookingCancelledKafkaListener;
import dakota.software.flightservice.infrastructure.kafka.BookingCreatedKafkaListener;
import dakota.software.flightservice.infrastructure.persistence.FlightJpaRepository;
import dakota.software.flightservice.infrastructure.persistence.FlightPersistenceAdapter;
import dakota.software.flightservice.infrastructure.persistence.ProcessedEventJpaRepository;
import dakota.software.flightservice.infrastructure.persistence.SeatInventoryJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AppFlightConfig {

    @Bean
    public FlightRepositoryPort flightRepositoryPort(FlightJpaRepository flightJpaRepository,
                                                     SeatInventoryJpaRepository seatInventoryJpaRepository) {
        return new FlightPersistenceAdapter(flightJpaRepository, seatInventoryJpaRepository);
    }

    // Un bean POR puerto-in: cada consumidor (controller web, listeners Kafka)
    // depende solo del caso de uso que necesita (ISP), nunca del servicio completo.
    @Bean
    public GetFlightsUseCase getFlightsUseCase(FlightRepositoryPort flightRepositoryPort) {
        return new FlightService(flightRepositoryPort);
    }

    @Bean
    public ReserveSeatsUseCase reserveSeatsUseCase(FlightRepositoryPort flightRepositoryPort) {
        return new FlightService(flightRepositoryPort);
    }

    @Bean
    public ReleaseSeatsUseCase releaseSeatsUseCase(FlightRepositoryPort flightRepositoryPort) {
        return new FlightService(flightRepositoryPort);
    }

    // Kafka listeners (consumers) — registrados como beans para que Spring los detecte
    @Bean
    public BookingCreatedKafkaListener bookingCreatedKafkaListener(ReserveSeatsUseCase reserveSeatsUseCase,
                                                                    ProcessedEventJpaRepository processedEventRepository) {
        return new BookingCreatedKafkaListener(reserveSeatsUseCase, processedEventRepository);
    }

    @Bean
    public BookingCancelledKafkaListener bookingCancelledKafkaListener(ReleaseSeatsUseCase releaseSeatsUseCase,
                                                                        ProcessedEventJpaRepository processedEventRepository) {
        return new BookingCancelledKafkaListener(releaseSeatsUseCase, processedEventRepository);
    }
}