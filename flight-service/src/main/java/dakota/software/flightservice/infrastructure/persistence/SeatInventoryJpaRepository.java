package dakota.software.flightservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatInventoryJpaRepository extends JpaRepository<SeatInventoryEntity, Long> {

    Optional<SeatInventoryEntity> findByFlightId(Long flightId);
}