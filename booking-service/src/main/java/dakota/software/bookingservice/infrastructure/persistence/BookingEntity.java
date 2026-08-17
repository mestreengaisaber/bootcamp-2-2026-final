package dakota.software.bookingservice.infrastructure.persistence;

import dakota.software.bookingservice.domain.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "bookings")
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passenger_id", nullable = false)
    private String passengerId;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(name = "passenger_email", nullable = false)
    private String passengerEmail;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Integer seats;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    protected BookingEntity() {
        // required by JPA
    }

    public BookingEntity(String passengerId, String passengerName, String passengerEmail,
                         Long flightId, Integer seats, BigDecimal amount, BookingStatus status) {
        this.passengerId = passengerId;
        this.passengerName = passengerName;
        this.passengerEmail = passengerEmail;
        this.flightId = flightId;
        this.seats = seats;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getPassengerEmail() {
        return passengerEmail;
    }

    public Long getFlightId() {
        return flightId;
    }

    public Integer getSeats() {
        return seats;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}