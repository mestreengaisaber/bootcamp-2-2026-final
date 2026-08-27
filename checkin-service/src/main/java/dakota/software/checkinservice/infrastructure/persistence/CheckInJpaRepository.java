package dakota.software.checkinservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckInJpaRepository extends JpaRepository<CheckInEntity, Long> {
    Optional<CheckInEntity> findByBookingId(Long bookingId);
}
