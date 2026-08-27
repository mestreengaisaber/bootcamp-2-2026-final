create table outbox_events (
    id bigserial primary key,
    event_type varchar(50) not null,
    payload text not null,
    created_at timestamp not null,
    published_at timestamp
);
