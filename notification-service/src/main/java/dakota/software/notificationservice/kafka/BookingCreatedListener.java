package dakota.software.notificationservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume booking.created de Kafka y genera una notificación de reserva creada.
 */
@Component
public class BookingCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCreatedListener.class);

    @KafkaListener(
            topics = "booking.created",
            properties = "spring.json.value.default.type=java.util.Map"
    )
    @SuppressWarnings("unchecked")
    public void handleBookingCreated(Map<String, Object> event) {
        Object bookingId = event.get("bookingId");
        Object flightId = event.get("flightId");
        String passengerEmail = (String) event.get("passengerEmail");

        log.info("Notificación: Reserva CREADA - Booking ID: {}, Vuelo: {}, Pasajero: {}",
                bookingId, flightId, passengerEmail);
    }
}
