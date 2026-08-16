package dakota.software.flightservice.infrastructure.config;

import dakota.software.flightservice.application.port.in.FlightUsecase;
import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.application.service.FlightService;
import dakota.software.flightservice.infrastructure.persistence.FlightJpaRepository;
import dakota.software.flightservice.infrastructure.persistence.FlightPersistenceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AppFlightConfig {

    @Bean
    public FlightRepositoryPort flightRepositoryPort(FlightJpaRepository flightJpaRepository) {
        return new FlightPersistenceAdapter(flightJpaRepository);
    }

    @Bean
    public FlightUsecase flightUsecase(FlightRepositoryPort flightRepositoryPort) {
        return new FlightService(flightRepositoryPort);
    }
}