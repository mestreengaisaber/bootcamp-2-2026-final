    create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(255) not null,
    aggregate_id varchar(255) not null,
    event_type varchar(255) not null,
    payload text not null,
    created_at timestamp not null,
    published_at timestamp null
);

create table processed_events (
    event_id varchar(255) primary key,
    processed_at timestamp not null
);
