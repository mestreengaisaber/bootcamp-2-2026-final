package dakota.software.flightservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_inventory")
public class SeatInventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false, unique = true)
    private FlightEntity flight;

    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    private Integer availableSeats;

    protected SeatInventoryEntity() {
        // required by JPA
    }

    public SeatInventoryEntity(FlightEntity flight, Integer totalSeats, Integer availableSeats) {
        this.flight = flight;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
    }

    public Long getId() {
        return id;
    }

    public FlightEntity getFlight() {
        return flight;
    }

    void setFlight(FlightEntity flight) {
        this.flight = flight;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }
}