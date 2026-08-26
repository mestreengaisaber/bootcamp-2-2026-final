-- Tabla de deduplicacion idempotente: cada eventId de booking.created /
-- booking.cancelled procesado se registra aqui. Si Kafka reentrega el mismo
-- evento, el listener lo detecta por PK y lo ignora (at-least-once -> efectivamente once).
create table processed_events (
    event_id varchar(255) primary key,
    processed_at timestamp not null
);
