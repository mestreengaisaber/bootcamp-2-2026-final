package dakota.software.flightservice.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeatInventoryJpaRepository extends JpaRepository<SeatInventoryEntity, Long> {

    Optional<SeatInventoryEntity> findByFlightId(Long flightId);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatInventoryEntity s where s.flight.id = :flightId")
    Optional<SeatInventoryEntity> findByFlightIdWithLock(@Param("flightId") Long flightId);
}