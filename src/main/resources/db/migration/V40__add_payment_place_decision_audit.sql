alter table payment_place_entries
    add column if not exists decided_by_user_id uuid,
    add column if not exists decided_by_name varchar(255),
    add column if not exists decided_at timestamp;
