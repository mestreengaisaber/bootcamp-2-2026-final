package dakota.software.flightservice.application.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de integracion: reserva creada (publicado por booking-service,
 * consumido por flight-service para reservar los asientos).
 * Copia local del contrato (doc de arquitectura seccion 6, Caso B): cada
 * servicio mantiene su propia copia; solo deben coincidir en el JSON.
 * Sin metodo de fabrica ni imports de dominio: el consumidor solo lee.
 */
public record BookingCreatedEvent(
        String eventId,
        String eventType,
        String aggregateId,
        int version,
        String occurredAt,
        String correlationId,
        String causationId,
        Long bookingId,
        String passengerId,
        Long flightId,
        int seats,
        BigDecimal amount,
        String passengerEmail,
        String paymentMethod,
        Instant createdAt
) {

    public static final String EVENT_TYPE = "BOOKING_CREATED";
    public static final int VERSION = 1;
}
