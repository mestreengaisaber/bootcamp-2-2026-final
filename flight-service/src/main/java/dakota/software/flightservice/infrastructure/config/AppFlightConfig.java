package dakota.software.flightservice.infrastructure.config;

import dakota.software.flightservice.application.port.in.FlightUsecase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.application.service.FlightService;
import dakota.software.flightservice.infrastructure.persistence.FlightJpaRepository;
import dakota.software.flightservice.infrastructure.persistence.FlightPersistenceAdapter;
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

    @Bean
    public FlightUsecase flightUsecase(FlightRepositoryPort flightRepositoryPort) {
        return new FlightService(flightRepositoryPort);
    }
}