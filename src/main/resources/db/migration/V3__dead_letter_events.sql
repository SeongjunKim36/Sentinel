create table if not exists dead_letter_event (
    id uuid primary key,
    source_stage varchar(50) not null,
    source_topic varchar(100) not null,
    tenant_id varchar(100) not null,
    event_id uuid not null,
    channel varchar(50),
    reason text not null,
    payload_type varchar(50) not null,
    payload text not null,
    status varchar(30) not null default 'OPEN',
    replay_count integer not null default 0,
    created_at timestamptz not null default now(),
    last_replay_at timestamptz,
    last_replay_error text
);

create index if not exists idx_dead_letter_event_status on dead_letter_event (status);
create index if not exists idx_dead_letter_event_tenant_id on dead_letter_event (tenant_id);
create index if not exists idx_dead_letter_event_channel on dead_letter_event (channel);
create index if not exists idx_dead_letter_event_created_at on dead_letter_event (created_at desc);
