package dakota.software.checkinservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, String> {

    @Query("SELECT e FROM ProcessedEventEntity e WHERE e.eventType = :eventType AND e.bookingId = :bookingId")
    List<ProcessedEventEntity> findByEventTypeAndBookingId(@Param("eventType") String eventType, @Param("bookingId") Long bookingId);
}
