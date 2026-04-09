create table if not exists delivery_attempt (
    id bigserial primary key,
    analysis_result_id uuid not null,
    event_id uuid not null,
    tenant_id varchar(100) not null,
    channel varchar(50) not null,
    success boolean not null,
    external_id varchar(255),
    message text,
    attempted_at timestamptz not null default now()
);

create index if not exists idx_delivery_attempt_event_id on delivery_attempt (event_id);
create index if not exists idx_delivery_attempt_tenant_id on delivery_attempt (tenant_id);
create index if not exists idx_delivery_attempt_channel on delivery_attempt (channel);
create index if not exists idx_delivery_attempt_success on delivery_attempt (success);
create index if not exists idx_delivery_attempt_attempted_at on delivery_attempt (attempted_at desc);
