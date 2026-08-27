package dakota.software.checkinservice.domain;

import java.time.Instant;

public class CheckIn {

    private Long id;                    // asignado por BD
    private final Long bookingId;       // invariante
    private final Long flightId;        // invariante
    private final Long passengerId;     // invariante                                                                                    ▄
    private CheckInStatus status;       // mutable: PENDING -> COMPLETED
    private final Instant createdAt;    // invariante
    private Instant completedAt;        // null hasta complete()
    private BoardingPass boardingPass;  // null hasta complete()

    // Constructor de CREACIÓN (factory create)
    private CheckIn(Long bookingId, Long flightId, Long passengerId) {
        this.bookingId = bookingId;      // final
        this.flightId = flightId;        // final
        this.passengerId = passengerId;  // final
        this.createdAt = Instant.now();  // final (se fija AHORA)

        // Estado inicial del ciclo de vida (NO final, empiezan null/default)                                                                ▄
        this.status = CheckInStatus.PENDING;
        this.completedAt = null;
        this.boardingPass = null;
    }

    //constructor de restauracion (recupera de BD) checkin.restore

    private CheckIn(Long id, Long bookingId, Long flightId, Long passengerId,
                    CheckInStatus status, Instant createdAt, Instant completedAt,
                    BoardingPass boardingPass) {
        this.id = id;                    // La BD YA lo asignó
        this.bookingId = bookingId;      // Tal cual está en BD
        this.flightId = flightId;
        this.passengerId = passengerId;
        this.status = status;            // Puede ser COMPLETED (¡no tocamos!)
        this.createdAt = createdAt;      // Tal cual (histórico)
        this.completedAt = completedAt;  // Puede tener valor
        this.boardingPass = boardingPass; // Puede tener valor
    }

    // Factory method: crear CheckIn nuevo
    public static CheckIn create(Long bookingId, Long flightId, Long passengerId) {
        if (bookingId == null || flightId == null || passengerId == null) {
            throw new IllegalArgumentException("bookingId, flightId, passengerId son obligatorios");
        }
        return new CheckIn(bookingId, flightId, passengerId);
    }

    // Factory method: restaurar CheckIn desde BD
    public static CheckIn restore(Long id, Long bookingId, Long flightId, Long passengerId,
                                   CheckInStatus status, Instant createdAt, Instant completedAt,
                                   BoardingPass boardingPass) {
        return new CheckIn(id, bookingId, flightId, passengerId, status, createdAt, completedAt, boardingPass);
    }

    // Método de negocio: completar check-in
    public void complete(BoardingPass boardingPass) {
        ensurePending();
        this.boardingPass = boardingPass;
        this.status = CheckInStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    // Guarda: solo se puede completar desde PENDING
    private void ensurePending() {
        if (this.status != CheckInStatus.PENDING) {
            throw new IllegalStateException("Check-in ya completado: " + status);
        }
    }

    //getters
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

    public CheckInStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public BoardingPass getBoardingPass() {
        return boardingPass;
    }
}
