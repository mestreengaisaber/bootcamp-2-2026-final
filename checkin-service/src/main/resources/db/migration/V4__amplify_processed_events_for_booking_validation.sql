-- Se amplía processed_events para soportar la validación de reserva confirmada.
-- Caso de uso: No se puede hacer check-in sin reserva confirmada.
-- Al guardar los datos del evento BookingConfirmed, podemos validar
-- contra esta tabla en tiempo de check-in.
ALTER TABLE processed_events ADD COLUMN event_type VARCHAR(50);
ALTER TABLE processed_events ADD COLUMN booking_id BIGINT;
ALTER TABLE processed_events ADD COLUMN flight_id BIGINT;
ALTER TABLE processed_events ADD COLUMN passenger_id BIGINT;
