create table if not exists payment_place_batches (
    id uuid primary key,
    file_name varchar(255) not null,
    imported_by_user_id uuid,
    imported_by_name varchar(255),
    imported_by_email varchar(255),
    imported_at timestamp not null,
    status varchar(50) not null,
    total_entries integer not null default 0,
    audit_entries integer not null default 0,
    unlocated_agency_entries integer not null default 0,
    error_message text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists payment_place_entries (
    id uuid primary key,
    batch_id uuid not null references payment_place_batches(id) on delete cascade,
    section varchar(80) not null,
    external_id varchar(20) not null,
    client_code varchar(20),
    title_number varchar(60),
    due_date varchar(10),
    title_value varchar(30),
    paid_value varchar(30),
    occurrence varchar(255),
    payer_document varchar(30),
    payer_name varchar(255),
    client_city varchar(120),
    agency_city_pdf varchar(120),
    payer_city varchar(120),
    bank_agency varchar(20),
    bank_code varchar(10),
    agency_code varchar(20),
    occurrence_complement varchar(255),
    analysis_status varchar(60) not null,
    analyst_decision varchar(30),
    analyst_notes text,
    bank_name varchar(255),
    bacen_agency_name varchar(255),
    bacen_institution_name varchar(255),
    bacen_agency_city varchar(120),
    bacen_agency_address text,
    bacen_agency_zip_code varchar(20),
    distance_client_agency_km numeric(12, 2),
    distance_agency_payer_km numeric(12, 2),
    distance_client_payer_km numeric(12, 2),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_payment_place_batches_imported_at_desc
    on payment_place_batches (imported_at desc);

create index if not exists idx_payment_place_entries_batch_id
    on payment_place_entries (batch_id);

create index if not exists idx_payment_place_entries_status
    on payment_place_entries (analysis_status);

create index if not exists idx_payment_place_entries_payer_document
    on payment_place_entries (payer_document);
