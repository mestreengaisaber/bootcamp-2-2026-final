-- Boundary fixture: a flight departing exactly at midnight of the 21st.
-- Proves the half-open range (>= start AND < end): it must appear only for date=2026-08-21,
-- never for date=2026-08-20 (BETWEEN would have wrongly included it in both).

INSERT INTO flights (id, flight_number, origin_airport_id, destination_airport_id, departure_at, arrival_at, price)
VALUES (8, 'IBX999', 1, 6, TIMESTAMP '2026-08-21 00:00:00', TIMESTAMP '2026-08-21 01:30:00', 99.00);

INSERT INTO seat_inventory (flight_id, total_seats, available_seats) VALUES (8, 100, 100);