package dakota.software.flightservice.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.Optional;


public class SeatInventoryJpaRepositoryImpl implements SeatInventoryJpaRepositoryCustom {

    private final EntityManager entityManager;

    public SeatInventoryJpaRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<SeatInventoryEntity> findByFlightIdWithLock(Long flightId) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<SeatInventoryEntity> query =
                cb.createQuery(SeatInventoryEntity.class);

        Root<SeatInventoryEntity> root = query.from(SeatInventoryEntity.class);

        // Metamodelo: SeatInventoryEntity_.flight y FlightEntity_.id son
        // SingularAttribute tipados generados al compilar.
        query.select(root).where(
                cb.equal(
                        root.get(SeatInventoryEntity_.flight)
                                .get(FlightEntity_.id),
                        flightId));
        //como ya no usamos las interfaces nos obliga a definirlo en el createQuery.
        return entityManager.createQuery(query)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }
}
