package dakota.software.checkinservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de eventos ya procesados por el consumidor (idempotencia).
 * PK = eventId: un evento duplicado se ignora.
 *
 * Se amplía con campos de booking para soportar la validación
 * de reserva confirmada antes del check-in.
 * Caso de uso: No se puede hacer check-in sin reserva confirmada.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(nullable = false)
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "flight_id")
    private Long flightId;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId, String eventType, Long bookingId,
                                 Long flightId, Long passengerId, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.flightId = flightId;
        this.passengerId = passengerId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
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

    public Instant getProcessedAt() {
        return processedAt;
    }
}
