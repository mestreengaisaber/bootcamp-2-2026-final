package dakota.software.flightservice.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flights")
public class FlightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_airport_id", nullable = false)
    private AirportEntity origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_airport_id", nullable = false)
    private AirportEntity destination;

    @Column(nullable = false)
    private LocalDateTime departureAt;

    @Column(nullable = false)
    private LocalDateTime arrivalAt;

    @Column(nullable = false)
    private BigDecimal price;

    @OneToOne(mappedBy = "flight", cascade = CascadeType.ALL, orphanRemoval = true)
    private SeatInventoryEntity seatInventory;

    protected FlightEntity() {
        // required by JPA
    }

    public FlightEntity(String flightNumber,
                        AirportEntity origin,
                        AirportEntity destination,
                        LocalDateTime departureAt,
                        LocalDateTime arrivalAt,
                        BigDecimal price) {
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public AirportEntity getOrigin() {
        return origin;
    }

    public AirportEntity getDestination() {
        return destination;
    }

    public LocalDateTime getDepartureAt() {
        return departureAt;
    }

    public LocalDateTime getArrivalAt() {
        return arrivalAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public SeatInventoryEntity getSeatInventory() {
        return seatInventory;
    }

    public void setSeatInventory(SeatInventoryEntity seatInventory) {
        this.seatInventory = seatInventory;
        seatInventory.setFlight(this);
    }
}