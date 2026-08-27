package dakota.software.checkinservice.infrastructure.adapter;

import dakota.software.checkinservice.application.event.CheckInCompletedEvent;
import dakota.software.checkinservice.application.port.out.CheckInEventPublisherPort;
import dakota.software.checkinservice.domain.CheckIn;
import dakota.software.checkinservice.infrastructure.persistence.OutboxEvent;
import dakota.software.checkinservice.infrastructure.persistence.OutboxJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

public class CheckInEventOutboxAdapter implements CheckInEventPublisherPort {

    private final OutboxJpaRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public CheckInEventOutboxAdapter(OutboxJpaRepository outboxRepo, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void checkInCompleted(CheckIn checkIn) {
        CheckInCompletedEvent event = CheckInCompletedEvent.from(checkIn);

        String payload = objectMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = new OutboxEvent(
                CheckInCompletedEvent.EVENT_TYPE,
                payload,
                Instant.now()
        );
        outboxRepo.save(outboxEvent);
    }
}
