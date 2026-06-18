alter table payment_place_entries
    add column if not exists client_address text,
    add column if not exists payer_address text,
    add column if not exists agency_address_resolved text,
    add column if not exists agency_enriched_at timestamp;
