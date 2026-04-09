create table if not exists pipeline_config (
    id bigserial primary key,
    tenant_id varchar(100) not null,
    config jsonb not null,
    created_at timestamptz not null default now()
);

create table if not exists analysis_result (
    id uuid primary key,
    event_id uuid not null,
    tenant_id varchar(100) not null,
    category varchar(100) not null,
    severity varchar(20) not null,
    confidence double precision not null,
    summary text not null,
    payload jsonb not null,
    created_at timestamptz not null default now()
);
