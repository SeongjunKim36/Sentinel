create index if not exists idx_delivery_attempt_attempted_at_id
    on delivery_attempt (attempted_at desc, id desc);

create index if not exists idx_delivery_attempt_tenant_attempted_at_id
    on delivery_attempt (tenant_id, attempted_at desc, id desc);

create index if not exists idx_delivery_attempt_event_attempted_at_id
    on delivery_attempt (event_id, attempted_at desc, id desc);

create index if not exists idx_delivery_attempt_channel_attempted_at_id
    on delivery_attempt (channel, attempted_at desc, id desc);

create index if not exists idx_delivery_attempt_success_attempted_at_id
    on delivery_attempt (success, attempted_at desc, id desc);
