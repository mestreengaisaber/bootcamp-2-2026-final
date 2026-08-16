create table airports (
    id bigserial primary key,
    code varchar(10) not null unique,
    name varchar(255) not null,
    city varchar(255) not null,
    country varchar(255) not null
);

create table flights (
    id bigserial primary key,
    flight_number varchar(20) not null unique,
    origin_airport_id bigint not null references airports(id),
    destination_airport_id bigint not null references airports(id),
    departure_at timestamp not null,
    arrival_at timestamp not null,
    price numeric(10, 2) not null
);

create table seat_inventory (
    id bigserial primary key,
    flight_id bigint not null unique references flights(id),
    total_seats integer not null,
    available_seats integer not null
);