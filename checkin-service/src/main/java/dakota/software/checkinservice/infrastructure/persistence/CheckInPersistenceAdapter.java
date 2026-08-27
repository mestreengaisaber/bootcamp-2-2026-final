package dakota.software.checkinservice.infrastructure.persistence;

import dakota.software.checkinservice.application.port.out.CheckInRepositoryPort;
import dakota.software.checkinservice.domain.BoardingPass;
import dakota.software.checkinservice.domain.CheckIn;

import java.util.Optional;

public class CheckInPersistenceAdapter implements CheckInRepositoryPort {

    private final CheckInJpaRepository jpaRepo;

    public CheckInPersistenceAdapter(CheckInJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public CheckIn save(CheckIn checkIn) {
        CheckInEntity entity = toEntity(checkIn);
        CheckInEntity saved = jpaRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<CheckIn> findByBookingId(Long bookingId) {
        return jpaRepo.findByBookingId(bookingId)
                .map(this::toDomain);
    }

    private CheckInEntity toEntity(CheckIn checkIn) {
        BoardingPass bp = checkIn.getBoardingPass();
        return new CheckInEntity(
                checkIn.getBookingId(),
                checkIn.getFlightId(),
                checkIn.getPassengerId(),
                checkIn.getStatus(),
                checkIn.getCreatedAt(),
                checkIn.getCompletedAt(),
                bp != null ? bp.seatNumber() : null,
                bp != null ? bp.gate() : null,
                bp != null ? bp.boardingTime() : null
        );
    }

    private CheckIn toDomain(CheckInEntity entity) {
        BoardingPass bp = null;
        if (entity.getSeatNumber() != null) {
            bp = new BoardingPass(
                    null,
                    entity.getId(),
                    entity.getSeatNumber(),
                    entity.getGate(),
                    entity.getBoardingTime()
            );
        }
        return CheckIn.restore(
                entity.getId(),
                entity.getBookingId(),
                entity.getFlightId(),
                entity.getPassengerId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                bp
        );
    }
}
