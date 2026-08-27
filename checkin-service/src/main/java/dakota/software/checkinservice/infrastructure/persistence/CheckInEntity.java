package dakota.software.checkinservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins")
public class CheckInEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private dakota.software.checkinservice.domain.CheckInStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "seat_number")
    private String seatNumber;

    @Column(name = "gate")
    private String gate;

    @Column(name = "boarding_time")
    private LocalDateTime boardingTime;

    protected CheckInEntity() {
        // required by JPA
    }

    public CheckInEntity(Long bookingId, Long flightId, Long passengerId,
                         dakota.software.checkinservice.domain.CheckInStatus status,
                         Instant createdAt, Instant completedAt,
                         String seatNumber, String gate, LocalDateTime boardingTime) {
        this.bookingId = bookingId;
        this.flightId = flightId;
        this.passengerId = passengerId;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.seatNumber = seatNumber;
        this.gate = gate;
        this.boardingTime = boardingTime;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getFlightId() {
        return flightId;
    }

    public Long getPassengerId() {
        return passengerId;
    }

    public dakota.software.checkinservice.domain.CheckInStatus getStatus() {
        return status;
    }

    public void setStatus(dakota.software.checkinservice.domain.CheckInStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getGate() {
        return gate;
    }

    public void setGate(String gate) {
        this.gate = gate;
    }

    public LocalDateTime getBoardingTime() {
        return boardingTime;
    }

    public void setBoardingTime(LocalDateTime boardingTime) {
        this.boardingTime = boardingTime;
    }
}
