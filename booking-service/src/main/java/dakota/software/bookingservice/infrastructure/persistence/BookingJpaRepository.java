package dakota.software.bookingservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, Long> {
}