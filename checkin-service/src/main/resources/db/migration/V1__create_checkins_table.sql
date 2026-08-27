create table check_ins (
    id bigserial primary key,
    booking_id bigint not null,
    flight_id bigint not null,
    passenger_id bigint not null,
    status varchar(20) not null,
    created_at timestamp not null,
    completed_at timestamp,
    seat_number varchar(10),
    gate varchar(10),
    boarding_time timestamp
);
