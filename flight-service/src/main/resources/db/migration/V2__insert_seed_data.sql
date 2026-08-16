    -- Seed data for the flight search flow: airports, flights and seat inventories.

INSERT INTO airports (id, code, name, city, country) VALUES
    (1, 'MAD', 'Adolfo Suarez Madrid-Barajas', 'Madrid', 'Spain'),
    (2, 'BCN', 'Josep Tarradellas Barcelona-El Prat', 'Barcelona', 'Spain'),
    (3, 'LHR', 'Heathrow', 'London', 'United Kingdom'),
    (4, 'JFK', 'John F. Kennedy International', 'New York', 'United States'),
    (5, 'CDG', 'Charles de Gaulle', 'Paris', 'France'),
    (6, 'AMS', 'Amsterdam Schiphol', 'Amsterdam', 'Netherlands');

INSERT INTO flights (id, flight_number, origin_airport_id, destination_airport_id, departure_at, arrival_at, price) VALUES
    (1, 'IB1234', 1, 2, TIMESTAMP '2026-08-20 10:00:00', TIMESTAMP '2026-08-20 11:30:00', 199.90),
    (2, 'IB5678', 2, 1, TIMESTAMP '2026-08-20 14:00:00', TIMESTAMP '2026-08-20 15:30:00', 179.50),
    (3, 'IB9301', 1, 3, TIMESTAMP '2026-08-20 08:30:00', TIMESTAMP '2026-08-20 10:15:00', 249.00),
    (4, 'AA102',  1, 4, TIMESTAMP '2026-08-21 18:00:00', TIMESTAMP '2026-08-21 21:45:00', 599.90),
    (5, 'AF2450', 2, 5, TIMESTAMP '2026-08-21 09:00:00', TIMESTAMP '2026-08-21 11:10:00', 189.00),
    (6, 'KL1602', 3, 6, TIMESTAMP '2026-08-22 07:30:00', TIMESTAMP '2026-08-22 09:55:00', 159.00),
    (7, 'IB9302', 1, 3, TIMESTAMP '2026-08-22 12:00:00', TIMESTAMP '2026-08-22 13:45:00', 269.00);

INSERT INTO seat_inventory (flight_id, total_seats, available_seats) VALUES
    (1, 180, 150),
    (2, 180, 180),
    (3, 220, 220),
    (4, 300, 45),
    (5, 200, 0),
    (6, 160, 160),
    (7, 220, 220);