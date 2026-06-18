alter table payment_place_entries
    add column if not exists bacen_institution_type varchar(120),
    add column if not exists institution_category varchar(40),
    add column if not exists geographic_reliability varchar(40),
    add column if not exists geographic_reliability_reason text,
    add column if not exists automatic_suggestion varchar(40),
    add column if not exists automatic_confidence varchar(40),
    add column if not exists automatic_evidence text;

create index if not exists idx_payment_place_entries_institution_category
    on payment_place_entries (institution_category);

create index if not exists idx_payment_place_entries_geographic_reliability
    on payment_place_entries (geographic_reliability);

create index if not exists idx_payment_place_entries_automatic_suggestion
    on payment_place_entries (automatic_suggestion);
