package dakota.software.flightservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlightJpaRepository extends JpaRepository<FlightEntity, Long> {

    Optional<FlightEntity> findByFlightNumber(String flightNumber);

    @Query("select f from FlightEntity f where f.departureAt >= :start and f.departureAt < :end")
    @EntityGraph(attributePaths = {"origin", "destination", "seatInventory"})
    List<FlightEntity> findByDepartureAtRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select f from FlightEntity f
            where f.origin.code = :originCode and f.destination.code = :destinationCode
            and f.departureAt >= :from
            """)
    @EntityGraph(attributePaths = {"origin", "destination", "seatInventory"})
    List<FlightEntity> findByOriginAndDestinationAfter(@Param("originCode") String originCode,
                                                       @Param("destinationCode") String destinationCode,
                                                       @Param("from") LocalDateTime from);
}