package dakota.software.notificationservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume checkin.completed de Kafka y genera una notificación de check-in completado.
 */
@Component
public class CheckInCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(CheckInCompletedListener.class);

    @KafkaListener(
            topics = "checkin.completed",
            properties = "spring.json.value.default.type=java.util.Map"
    )
    @SuppressWarnings("unchecked")
    public void handleCheckInCompleted(Map<String, Object> event) {
        Object bookingId = event.get("bookingId");
        Object flightId = event.get("flightId");
        String seatNumber = (String) event.get("seatNumber");
        String gate = (String) event.get("gate");

        log.info("Notificación: Check-in COMPLETADO - Booking ID: {}, Vuelo: {}, Asiento: {}, Puerta: {}",
                bookingId, flightId, seatNumber, gate);
    }
}
