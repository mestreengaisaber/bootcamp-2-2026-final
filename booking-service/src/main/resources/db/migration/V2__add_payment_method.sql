alter table bookings
    add column payment_method varchar(50) not null default 'MOCK';
