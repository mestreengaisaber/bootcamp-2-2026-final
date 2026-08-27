package dakota.software.checkinservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, String> {
    boolean existsByEventTypeAndBookingId(String eventType, Long bookingId);
}
