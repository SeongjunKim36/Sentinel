create index if not exists idx_dead_letter_event_tenant_created_at_id
    on dead_letter_event (tenant_id, created_at desc, id desc);

create index if not exists idx_dead_letter_event_tenant_status_created_at_id
    on dead_letter_event (tenant_id, status, created_at desc, id desc);

create index if not exists idx_dead_letter_event_tenant_channel_created_at_id
    on dead_letter_event (tenant_id, channel, created_at desc, id desc);

create index if not exists idx_dead_letter_replay_audit_dead_letter_created_at_id
    on dead_letter_replay_audit (dead_letter_id, created_at desc, id desc);
