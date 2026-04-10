create table if not exists dead_letter_replay_audit (
    id bigserial primary key,
    dead_letter_id uuid not null references dead_letter_event (id) on delete cascade,
    outcome varchar(30) not null,
    status varchar(30) not null,
    message text not null,
    operator_note text,
    created_at timestamptz not null default now()
);

create index if not exists idx_dead_letter_replay_audit_dead_letter_id_created_at
    on dead_letter_replay_audit (dead_letter_id, created_at desc);
