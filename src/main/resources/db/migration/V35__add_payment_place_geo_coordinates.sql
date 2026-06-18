alter table payment_place_entries
    add column if not exists client_latitude numeric(10, 6),
    add column if not exists client_longitude numeric(10, 6),
    add column if not exists agency_latitude numeric(10, 6),
    add column if not exists agency_longitude numeric(10, 6),
    add column if not exists payer_latitude numeric(10, 6),
    add column if not exists payer_longitude numeric(10, 6);
