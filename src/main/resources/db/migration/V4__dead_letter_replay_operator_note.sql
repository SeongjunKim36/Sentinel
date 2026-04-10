alter table dead_letter_event
    add column if not exists last_replay_operator_note text;
