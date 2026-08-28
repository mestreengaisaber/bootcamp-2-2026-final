package dakota.software.notificationservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume payment.processed de Kafka y genera una notificación de pago procesado.
 */
@Component
public class PaymentProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    @KafkaListener(
            topics = "payment.processed",
            properties = "spring.json.value.default.type=java.util.Map"
    )
    @SuppressWarnings("unchecked")
    public void handlePaymentProcessed(Map<String, Object> event) {
        Object bookingId = event.get("bookingId");
        String status = (String) event.get("status");

        log.info("Notificación: Pago PROCESADO - Booking ID: {}, Estado: {}",
                bookingId, status);
    }
}
