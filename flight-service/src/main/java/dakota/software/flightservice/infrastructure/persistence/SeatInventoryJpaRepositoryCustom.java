package dakota.software.flightservice.infrastructure.persistence;

import java.util.Optional;

public interface SeatInventoryJpaRepositoryCustom {
    Optional<SeatInventoryEntity> findByFlightIdWithLock(Long flightId);
}
