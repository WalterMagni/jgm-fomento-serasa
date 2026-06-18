alter table payment_place_entries
    add column if not exists score_sacado integer,
    add column if not exists score_cedente integer;
