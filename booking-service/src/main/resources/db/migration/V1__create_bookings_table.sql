create table bookings (
    id bigserial primary key,
    passenger_id varchar(255) not null,
    passenger_name varchar(255) not null,
    passenger_email varchar(255) not null,
    flight_id bigint not null,
    seats integer not null,
    amount numeric(10, 2) not null,
    status varchar(20) not null
);