package dakota.software.flightservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirportJpaRepository extends JpaRepository<AirportEntity, Long> {

    Optional<AirportEntity> findByCode(String code);
}